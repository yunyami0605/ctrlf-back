package com.ctrlf.education.video.controller;

import com.ctrlf.education.script.client.InfraRagClient;
import com.ctrlf.education.video.dto.VideoDtos.InternalSourceSetDocumentsResponse;
import com.ctrlf.education.video.dto.VideoDtos.SourceSetCompleteCallback;
import com.ctrlf.education.video.dto.VideoDtos.SourceSetCompleteResponse;
import com.ctrlf.education.video.service.SourceSetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * 소스셋 내부 API 컨트롤러 (FastAPI ↔ Spring).
 * 내부 서비스 간 통신용으로 X-Internal-Token 인증을 사용합니다.
 */
@Slf4j
@Tag(name = "Internal - SourceSet", description = "소스셋 내부 API (FastAPI ↔ Spring)")
@RestController
@RequestMapping("/internal")
@SecurityRequirement(name = "internal-token")
@RequiredArgsConstructor
public class InternalSourceSetController {

    private final SourceSetService sourceSetService;
    private final InfraRagClient infraRagClient;

    /**
     * 소스셋 문서 목록 조회 (FastAPI → Spring).
     * FastAPI가 sourceSet에 포함된 RagDocument 목록을 조회합니다.
     */
    @GetMapping("/source-sets/{sourceSetId}/documents")
    @Operation(
        summary = "소스셋 문서 목록 조회 (내부 API)",
        description = "FastAPI가 sourceSet에 포함된 RagDocument 목록을 조회합니다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(schema = @Schema(implementation = InternalSourceSetDocumentsResponse.class))),
        @ApiResponse(responseCode = "401", description = "내부 토큰 오류"),
        @ApiResponse(responseCode = "404", description = "소스셋을 찾을 수 없음")
    })
    public ResponseEntity<InternalSourceSetDocumentsResponse> getSourceSetDocuments(
        @Parameter(description = "소스셋 ID", required = true)
        @PathVariable UUID sourceSetId
    ) {
        return ResponseEntity.ok(sourceSetService.getSourceSetDocuments(sourceSetId));
    }

    /**
     * 소스셋 완료 콜백 (FastAPI → Spring).
     * sourceSet 오케스트레이션 완료 결과를 Spring에 전달합니다.
     */
    @PostMapping("/callbacks/source-sets/{sourceSetId}/complete")
    @Operation(
        summary = "소스셋 완료 콜백 (내부 API)",
        description = "sourceSet 오케스트레이션 완료 결과를 Spring에 전달합니다 (성공/실패)."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "콜백 처리 성공",
            content = @Content(schema = @Schema(implementation = SourceSetCompleteResponse.class))),
        @ApiResponse(responseCode = "400", description = "잘못된 요청"),
        @ApiResponse(responseCode = "401", description = "내부 토큰 오류"),
        @ApiResponse(responseCode = "404", description = "소스셋을 찾을 수 없음")
    })
    public ResponseEntity<SourceSetCompleteResponse> handleSourceSetComplete(
        @Parameter(description = "소스셋 ID", required = true)
        @PathVariable UUID sourceSetId,
        @Valid @RequestBody SourceSetCompleteCallback callback
    ) {
        return ResponseEntity.ok(sourceSetService.handleSourceSetComplete(sourceSetId, callback));
    }

    /**
     * S3 Presigned 다운로드 URL 조회 (FastAPI → Spring → Infra).
     * FastAPI가 S3 URL을 presigned URL로 변환하기 위해 호출합니다.
     */
    @PostMapping("/s3/download")
    @Operation(
        summary = "S3 Presigned 다운로드 URL 조회 (내부 API)",
        description = "FastAPI가 S3 URL을 presigned URL로 변환하기 위해 호출합니다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(schema = @Schema(implementation = S3DownloadResponse.class))),
        @ApiResponse(responseCode = "400", description = "잘못된 요청"),
        @ApiResponse(responseCode = "401", description = "내부 토큰 오류")
    })
    public ResponseEntity<S3DownloadResponse> getS3PresignedUrl(
        @Valid @RequestBody S3DownloadRequest request
    ) {
        String fileUrl = request.getFileUrl();
        log.info("S3 Presigned URL 요청 수신: fileUrl={}", fileUrl);
        
        // infra-service의 /infra/files/presign/download API를 호출
        String infraBaseUrl = infraRagClient.getBaseUrl();
        String targetUrl = infraBaseUrl + "/infra/files/presign/download";
        log.debug("Infra service 호출: baseUrl={}, targetUrl={}", infraBaseUrl, targetUrl);
        
        RestClient restClient = RestClient.builder()
            .baseUrl(infraBaseUrl)
            .build();

        try {
            Map<String, String> response = restClient.post()
                .uri("/infra/files/presign/download")
                .body(Map.of("fileUrl", fileUrl))
                .retrieve()
                .body(Map.class);

            String downloadUrl = response != null ? (String) response.get("downloadUrl") : null;
            if (downloadUrl == null) {
                log.error("Infra service 응답에 downloadUrl 없음: response={}", response);
                throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to get presigned URL from infra-service"
                );
            }

            log.info("S3 Presigned URL 생성 성공: fileUrl={}, presignedUrl_length={}", 
                fileUrl, downloadUrl != null ? downloadUrl.length() : 0);
            return ResponseEntity.ok(new S3DownloadResponse(downloadUrl));
        } catch (org.springframework.web.client.RestClientException e) {
            log.error("Infra service 호출 실패: fileUrl={}, error={}", fileUrl, e.getMessage(), e);
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                "Failed to get presigned URL from infra-service: " + e.getMessage()
            );
        }
    }

    /**
     * S3 Presigned URL 조회 요청 DTO.
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class S3DownloadRequest {
        @NotBlank(message = "fileUrl은 필수입니다")
        @Schema(description = "S3 파일 URL", example = "s3://bucket/docs/file.pdf")
        private String fileUrl;
    }

    /**
     * S3 Presigned URL 조회 응답 DTO.
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class S3DownloadResponse {
        @Schema(description = "Presigned 다운로드 URL")
        private String downloadUrl;
    }
}
