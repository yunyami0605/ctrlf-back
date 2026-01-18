package com.ctrlf.education.video.controller;

import com.ctrlf.education.video.dto.VideoDtos.LastVideoProgressResponse;
import com.ctrlf.education.video.dto.VideoDtos.VideoCompleteCallback;
import com.ctrlf.education.video.dto.VideoDtos.VideoCompleteResponse;
import com.ctrlf.education.video.service.VideoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 영상 생성 내부 API 컨트롤러 (AI 서버 ↔ Spring).
 */
@Tag(name = "Internal - Video", description = "영상 생성 내부 API (AI 서버 ↔ Spring)")
@RestController
@RequestMapping("/internal/video")
@SecurityRequirement(name = "internal-token")
@RequiredArgsConstructor
public class InternalVideoController {

    private final VideoService videoService;

    /**
     * AI 서버로부터 영상 생성 완료 콜백을 수신합니다.
     * (AI 서버 → 백엔드)
     *
     * @param jobId    Job ID
     * @param callback 콜백 데이터
     * @return 저장 결과
     */
    @Operation(summary = "영상 생성 완료 콜백 (AI -> 백엔드)", 
        description = "AI 서버가 영상 생성 완료 후 백엔드로 결과를 전달합니다. (내부 API)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "콜백 처리 성공",
            content = @Content(schema = @Schema(implementation = VideoCompleteResponse.class))),
        @ApiResponse(responseCode = "400", description = "잘못된 요청",
            content = @Content),
        @ApiResponse(responseCode = "401", description = "내부 토큰 오류"),
        @ApiResponse(responseCode = "404", description = "Job을 찾을 수 없음",
            content = @Content)
    })
    @PostMapping("/job/{jobId}/complete")
    public ResponseEntity<VideoCompleteResponse> handleVideoComplete(
        @Parameter(description = "Job ID", required = true)
        @PathVariable UUID jobId,
        @Valid @RequestBody VideoCompleteCallback callback
    ) {
        return ResponseEntity.ok(videoService.handleVideoComplete(jobId, callback));
    }

    /**
     * 사용자의 마지막 시청 영상 정보를 조회합니다 (이어보기용).
     * (infra-service → education-service)
     *
     * @param userId 사용자 UUID (X-User-Id 헤더)
     * @return 마지막 시청 영상 정보 (없으면 404)
     */
    @Operation(summary = "마지막 시청 영상 조회 (이어보기)",
        description = "사용자의 마지막 시청 영상 정보를 조회합니다. Q4 (교육 이어보기) 인텐트 처리용.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(schema = @Schema(implementation = LastVideoProgressResponse.class))),
        @ApiResponse(responseCode = "404", description = "시청 기록 없음",
            content = @Content),
        @ApiResponse(responseCode = "401", description = "내부 토큰 오류")
    })
    @GetMapping("/last-progress")
    public ResponseEntity<LastVideoProgressResponse> getLastVideoProgress(
        @Parameter(description = "사용자 UUID", required = true)
        @RequestHeader("X-User-Id") String userId
    ) {
        UUID userUuid = UUID.fromString(userId);
        LastVideoProgressResponse response = videoService.getLastVideoProgress(userUuid);

        if (response == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(response);
    }
}

