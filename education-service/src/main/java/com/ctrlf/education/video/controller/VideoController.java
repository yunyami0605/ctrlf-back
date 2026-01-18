package com.ctrlf.education.video.controller;

import com.ctrlf.education.video.dto.VideoDtos.JobItem;
import com.ctrlf.education.video.dto.VideoDtos.VideoJobRequest;
import com.ctrlf.education.video.dto.VideoDtos.VideoJobResponse;
import com.ctrlf.education.video.dto.VideoDtos.VideoJobUpdateRequest;
import com.ctrlf.education.video.dto.VideoDtos.VideoMetaItem;
import com.ctrlf.education.video.dto.VideoDtos.VideoRetryResponse;
import com.ctrlf.education.video.service.VideoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 영상 생성 관련 REST API 컨트롤러.
 */
@Tag(name = "Video", description = "영상 생성 관련 API")
@RestController
@RequestMapping("/video")
@RequiredArgsConstructor
public class VideoController {

    private final VideoService videoService;

    // 스크립트 관련 API는 EducationScriptController로 분리됨

    // ========================
    // 영상 생성 Job 관련 API
    // ========================

    /**
     * 영상 생성 Job 목록을 페이징으로 조회합니다.
     */
    @Operation(summary = "영상 생성 Job 목록 조회 (* 개발용)", description = "영상 생성 Job 목록을 페이징으로 조회합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(schema = @Schema(implementation = JobItem.class)))
    })
    @GetMapping("/jobs")
    public ResponseEntity<List<JobItem>> listJobs(
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(videoService.listJobs(page, size));
    }

    /**
     * 영상 생성 Job 상세 조회.
     */
    @Operation(summary = "영상 생성 Job 상세 조회 (프론트 -> 백엔드)", description = "특정 Job의 상세 정보를 조회합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(schema = @Schema(implementation = JobItem.class))),
        @ApiResponse(responseCode = "404", description = "Job을 찾을 수 없음", content = @Content)
    })
    @GetMapping("/job/{jobId}")
    public ResponseEntity<JobItem> getJob(
        @Parameter(description = "Job ID", required = true)
        @PathVariable UUID jobId
    ) {
        return ResponseEntity.ok(videoService.getJob(jobId));
    }

    /**
     * 영상 생성 Job 수정.
     */
    @Operation(summary = "영상 생성 Job 수정 (* 개발용)", description = "Job 상태/비고 등을 수정합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "수정 성공",
            content = @Content(schema = @Schema(implementation = JobItem.class))),
        @ApiResponse(responseCode = "404", description = "Job을 찾을 수 없음", content = @Content)
    })
    @PutMapping("/job/{jobId}")
    public ResponseEntity<JobItem> updateJob(
        @Parameter(description = "Job ID", required = true)
        @PathVariable UUID jobId,
        @Valid @RequestBody VideoJobUpdateRequest request
    ) {
        return ResponseEntity.ok(videoService.updateJob(jobId, request));
    }

    /**
     * 영상 생성 Job 삭제.
     */
    @Operation(summary = "영상 생성 Job 삭제 (* 개발용)", description = "특정 Job을 삭제합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "삭제 성공"),
        @ApiResponse(responseCode = "404", description = "Job을 찾을 수 없음", content = @Content)
    })
    @DeleteMapping("/job/{jobId}")
    public ResponseEntity<Void> deleteJob(
        @Parameter(description = "Job ID", required = true)
        @PathVariable UUID jobId
    ) {
        videoService.deleteJob(jobId);
        return ResponseEntity.noContent().build();
    }
    /**
     * 영상 생성 Job을 등록합니다.
     *
     * @param request 영상 생성 요청
     * @return Job 등록 결과
     */
    @Operation(summary = "영상 생성 요청 (프론트 -> 백엔드)", 
        description = "최종 확정된 스크립트를 기반으로 영상 생성 Job을 등록합니다. (권한: ROLE_ADMIN)")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Job 생성 성공",
            content = @Content(schema = @Schema(implementation = VideoJobResponse.class))),
        @ApiResponse(responseCode = "400", description = "scriptId 누락",
            content = @Content),
        @ApiResponse(responseCode = "403", description = "권한 없음",
            content = @Content),
        @ApiResponse(responseCode = "404", description = "script/eduId 없음",
            content = @Content),
        @ApiResponse(responseCode = "500", description = "Job 등록 실패",
            content = @Content)
    })
    @PostMapping("/job")
    public ResponseEntity<VideoJobResponse> createVideoJob(
        @Valid @RequestBody VideoJobRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(videoService.createVideoJob(request));
    }

    /**
     * 실패한 영상 생성 Job을 재시도합니다.
     *
     * @param jobId 재시도할 Job ID
     * @return 재시도 결과
     */
    @Operation(summary = "영상 생성 재시도 (프론트 -> 백엔드)", 
        description = "이전 영상 생성 Job이 FAILED 상태일 때 재시도합니다. (권한: ROLE_ADMIN)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "재시도 성공",
            content = @Content(schema = @Schema(implementation = VideoRetryResponse.class))),
        @ApiResponse(responseCode = "400", description = "job 상태가 FAILED가 아님",
            content = @Content),
        @ApiResponse(responseCode = "401", description = "인증 실패",
            content = @Content),
        @ApiResponse(responseCode = "403", description = "ROLE_ADMIN 아님",
            content = @Content),
        @ApiResponse(responseCode = "404", description = "jobId 없음",
            content = @Content),
        @ApiResponse(responseCode = "409", description = "이미 실행 중(PROCESSING)인 job 재시도 불가",
            content = @Content),
        @ApiResponse(responseCode = "500", description = "AI 서버 재요청 실패",
            content = @Content)
    })
    @PostMapping("/job/{jobId}/retry")
    public ResponseEntity<VideoRetryResponse> retryVideoJob(
        @Parameter(description = "Job ID", required = true)
        @PathVariable UUID jobId
    ) {
        return ResponseEntity.ok(videoService.retryVideoJob(jobId));
    }


    // ========================
    // 영상 메타 조회 API
    // ========================

    /**
     * 영상 상세 조회.
     */
    @Operation(summary = "영상 상세 조회 (프론트 -> 백엔드)", description = "영상 메타 정보를 조회합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "성공",
            content = @Content(schema = @Schema(implementation = VideoMetaItem.class))),
        @ApiResponse(responseCode = "404", description = "영상을 찾을 수 없음", content = @Content)
    })
    @GetMapping("/{videoId}")
    public ResponseEntity<VideoMetaItem> getVideo(
        @Parameter(description = "영상 ID", required = true) @PathVariable UUID videoId) {
        return ResponseEntity.ok(videoService.getVideoContent(videoId));
    }
}
