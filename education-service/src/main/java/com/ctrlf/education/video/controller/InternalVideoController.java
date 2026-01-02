package com.ctrlf.education.video.controller;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 영상 생성 내부 API 컨트롤러 (AI 서버 ↔ Spring).
 * 내부 서비스 간 통신용으로 X-Internal-Token 인증을 사용합니다.
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
}

