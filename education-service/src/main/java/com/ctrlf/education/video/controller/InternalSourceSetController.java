package com.ctrlf.education.video.controller;

import com.ctrlf.education.script.dto.EducationScriptDto.RenderSpecResponse;
import com.ctrlf.education.script.service.ScriptService;
import com.ctrlf.education.video.dto.VideoDtos.InternalSourceSetDocumentsResponse;
import com.ctrlf.education.video.dto.VideoDtos.S3DownloadRequest;
import com.ctrlf.education.video.dto.VideoDtos.S3DownloadResponse;
import com.ctrlf.education.video.dto.VideoDtos.SourceSetCompleteCallback;
import com.ctrlf.education.video.dto.VideoDtos.SourceSetCompleteResponse;
import com.ctrlf.education.video.service.SourceSetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    private final ScriptService scriptService;

    /**
     * 스크립트 렌더 스펙 조회 (FastAPI → Spring).
     * AI 서버가 렌더링을 위해 스크립트의 챕터/씬 정보를 조회합니다.
     */
    @GetMapping("/scripts/{scriptId}/render-spec")
    @Operation(
        summary = "스크립트 렌더 스펙 조회 (AI -> BACK)",
        description = "AI 서버가 렌더링을 위해 스크립트의 챕터/씬 정보를 조회합니다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(schema = @Schema(implementation = RenderSpecResponse.class))),
        @ApiResponse(responseCode = "401", description = "내부 토큰 오류"),
        @ApiResponse(responseCode = "404", description = "스크립트를 찾을 수 없음")
    })
    public ResponseEntity<RenderSpecResponse> getRenderSpec(
        @Parameter(description = "스크립트 ID", required = true)
        @PathVariable UUID scriptId
    ) {
        log.info("렌더 스펙 조회 요청: scriptId={}", scriptId);
        return ResponseEntity.ok(scriptService.getRenderSpec(scriptId));
    }

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
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        description = "소스셋 완료 콜백 요청",
        required = true,
        content = @Content(
            schema = @Schema(implementation = SourceSetCompleteCallback.class),
            examples = {
                @ExampleObject(
                    name = "성공 예시",
                    value = "{\n" +
                    "  \"videoId\": \"636b70b4-**\",\n" +
                    "  \"status\": \"COMPLETED\",\n" +
                    "  \"sourceSetStatus\": \"SCRIPT_READY\",\n" +
                    "  \"documents\": [\n" +
                    "    {\n" +
                    "      \"documentId\": \"465683e8-**7\",\n" +
                    "      \"status\": \"COMPLETED\",\n" +
                    "      \"failReason\": \"\"\n" +
                    "    }\n" +
                    "  ],\n" +
                    "  \"script\": {\n" +
                    "    \"scriptId\": \"eb4ad2cd-**\",\n" +
                    "    \"educationId\": \"fc5efdd4-**\",\n" +
                    "    \"sourceSetId\": \"eb4ad2cd-**\",\n" +
                    "    \"title\": \"직장내괴롭힘 예방 교육\",\n" +
                    "    \"totalDurationSec\": 1800,\n" +
                    "    \"version\": 1,\n" +
                    "    \"llmModel\": \"gpt-4o\",\n" +
                    "    \"chapters\": [\n" +
                    "      {\n" +
                    "        \"chapterIndex\": 0,\n" +
                    "        \"title\": \"직장내괴롭힘의 정의와 유형\",\n" +
                    "        \"durationSec\": 600,\n" +
                    "        \"scenes\": [\n" +
                    "          {\n" +
                    "            \"sceneIndex\": 0,\n" +
                    "            \"purpose\": \"인트로\",\n" +
                    "            \"narration\": \"안녕하세요. 직장내괴롭힘 예방 교육에 오신 것을 환영합니다.\",\n" +
                    "            \"caption\": \"직장내괴롭힘 예방 교육\",\n" +
                    "            \"visual\": \"타이틀 슬라이드\",\n" +
                    "            \"durationSec\": 30,\n" +
                    "            \"confidenceScore\": 0.95,\n" +
                    "            \"sourceRefs\": [\n" +
                    "              {\n" +
                    "                \"documentId\": \"465683e8-**\",\n" +
                    "                \"chunkIndex\": 0\n" +
                    "              }\n" +
                    "            ]\n" +
                    "          },\n" +
                    "          {\n" +
                    "            \"sceneIndex\": 1,\n" +
                    "            \"purpose\": \"정의 설명\",\n" +
                    "            \"narration\": \"직장내괴롭힘이란 업무상 지위를 이용하여 지속적으로 신체적, 정신적 고통을 주는 행위를 말합니다. 모든 사람은 평등합니다. 폭력은 사용해서는 안됩니다. 나이, 성별, 지위 상관없이 괴롭히면 안됩니다.\",\n" +
                    "            \"caption\": \"직장내괴롭힘의 정의\",\n" +
                    "            \"visual\": \"키 포인트: 업무상 지위 이용, 지속적 행위\",\n" +
                    "            \"durationSec\": 45,\n" +
                    "            \"confidenceScore\": 0.92,\n" +
                    "            \"sourceRefs\": [\n" +
                    "              {\n" +
                    "                \"documentId\": \"465683e8-**\",\n" +
                    "                \"chunkIndex\": 1\n" +
                    "              }\n" +
                    "            ]\n" +
                    "          }\n" +
                    "        ]\n" +
                    "      },\n" +
                    "      {\n" +
                    "        \"chapterIndex\": 1,\n" +
                    "        \"title\": \"직장내괴롭힘 예방 방법\",\n" +
                    "        \"durationSec\": 700,\n" +
                    "        \"scenes\": [\n" +
                    "          {\n" +
                    "            \"sceneIndex\": 0,\n" +
                    "            \"purpose\": \"예방 원칙\",\n" +
                    "            \"narration\": \"직장내괴롭힘을 예방하기 위해서는 상호 존중과 배려가 중요합니다. 때리는 것 뿐만 아니라 대화를 통해서도 괴롭힘이 발생할 수 있습니다. 법률 15조에 의하면, 신체적 폭행 뿐만 아니라 언어적 폭력도 정신적으로 피해를 주면 최대 1년까지 형을 받습니다.\",\n" +
                    "            \"caption\": \"상호 존중과 배려\",\n" +
                    "            \"visual\": \"다이어그램: 상호 존중 → 배려 → 예방\",\n" +
                    "            \"durationSec\": 60,\n" +
                    "            \"confidenceScore\": 0.90,\n" +
                    "            \"sourceRefs\": [\n" +
                    "              {\n" +
                    "                \"documentId\": \"465683e8-**\",\n" +
                    "                \"chunkIndex\": 5\n" +
                    "              }\n" +
                    "            ]\n" +
                    "          }\n" +
                    "        ]\n" +
                    "      }\n" +
                    "    ]\n" +
                    "  },\n" +
                    "  \"errorCode\": null,\n" +
                    "  \"errorMessage\": null,\n" +
                    "  \"requestId\": \"3fa85f64-5717-4562-b3fc-2c963f66afa6\",\n" +
                    "  \"traceId\": \"trace-12345\"\n" +
                    "}"
                )
            }
        )
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
        return ResponseEntity.ok(sourceSetService.getPresignedDownloadUrl(request.fileUrl()));
    }
}
