package com.ctrlf.education.video.controller;

import com.ctrlf.common.security.SecurityUtils;
import com.ctrlf.education.video.dto.VideoDtos.VideoCreateRequest;
import com.ctrlf.education.video.dto.VideoDtos.VideoCreateResponse;
import com.ctrlf.education.video.dto.VideoDtos.VideoMetaItem;
import com.ctrlf.education.video.dto.VideoDtos.VideoMetaUpdateRequest;
import com.ctrlf.education.video.dto.VideoDtos.VideoRejectRequest;
import com.ctrlf.education.video.dto.VideoDtos.AuditHistoryResponse;
import com.ctrlf.education.video.dto.VideoDtos.ReviewDetailResponse;
import com.ctrlf.education.video.dto.VideoDtos.ReviewQueueResponse;
import com.ctrlf.education.video.dto.VideoDtos.ReviewStatsResponse;
import com.ctrlf.education.video.dto.VideoDtos.VideoStatus;
import com.ctrlf.education.video.dto.VideoDtos.VideoStatusResponse;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@Tag(name = "Education-Admin Video", description = "교육 영상 메타 관리 API (ADMIN)")
@RestController
@RequestMapping("/admin/videos")
@SecurityRequirement(name = "bearer-jwt")
@RequiredArgsConstructor
public class AdminVideoController {

  private final VideoService videoService;

  // ========================
  // 영상 컨텐츠 생성 플로우 API
  // ========================

  @Operation(summary = "영상 컨텐츠 생성 (프론트 -> 백엔드)", description = "DRAFT 상태의 새 교육 영상 컨텐츠를 생성합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "생성됨",
      content = @Content(schema = @Schema(implementation = VideoCreateResponse.class))),
    @ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content)
  })
  @PostMapping
  public ResponseEntity<VideoCreateResponse> createVideo(
      @Valid @RequestBody VideoCreateRequest req,
      @AuthenticationPrincipal Jwt jwt) {
    UUID creatorUuid = SecurityUtils.extractUserUuid(jwt)
        .orElseThrow(() -> new IllegalArgumentException("사용자 UUID를 추출할 수 없습니다."));
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(videoService.createVideoContent(req, creatorUuid));
  }

  @Operation(summary = "검토 요청 (프론트 -> 백엔드)", description = "영상 생성 완료 후 검토자에게 검토를 요청합니다. 1차 검토 요청: SCRIPT_READY → SCRIPT_REVIEW_REQUESTED (스크립트 검토), 2차 검토 요청: READY → FINAL_REVIEW_REQUESTED (영상 검토)")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공",
      content = @Content(schema = @Schema(implementation = VideoStatusResponse.class))),
    @ApiResponse(responseCode = "400", description = "상태 변경 불가", content = @Content),
    @ApiResponse(responseCode = "404", description = "영상을 찾을 수 없음", content = @Content)
  })
  @PutMapping("/{videoId}/review-request")
  public ResponseEntity<VideoStatusResponse> requestReview(
      @Parameter(description = "영상 ID", required = true) @PathVariable UUID videoId) {
    return ResponseEntity.ok(videoService.requestReview(videoId));
  }

  @Operation(summary = "검토 승인 (프론트 -> 백엔드)", description = "검토자가 영상을 승인합니다. 1차 승인: SCRIPT_REVIEW_REQUESTED → SCRIPT_APPROVED (스크립트 승인, 영상 생성 가능), 2차 승인: FINAL_REVIEW_REQUESTED → PUBLISHED (영상 승인 = 게시)")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공",
      content = @Content(schema = @Schema(implementation = VideoStatusResponse.class))),
    @ApiResponse(responseCode = "400", description = "상태 변경 불가", content = @Content),
    @ApiResponse(responseCode = "404", description = "영상을 찾을 수 없음", content = @Content)
  })
  @PutMapping("/{videoId}/approve")
  public ResponseEntity<VideoStatusResponse> approveVideo(
      @Parameter(description = "영상 ID", required = true) @PathVariable UUID videoId) {
    return ResponseEntity.ok(videoService.approveVideo(videoId));
  }

  @Operation(summary = "검토 반려 (프론트 -> 백엔드)", description = "검토자가 영상을 반려합니다. 1차 반려: SCRIPT_REVIEW_REQUESTED → SCRIPT_READY (스크립트 검토 단계 반려), 2차 반려: FINAL_REVIEW_REQUESTED → READY (영상 검토 단계 반려). 반려 사유(reason)가 제공되면 EducationVideoReview 테이블에 저장됩니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공",
      content = @Content(schema = @Schema(implementation = VideoStatusResponse.class))),
    @ApiResponse(responseCode = "400", description = "상태 변경 불가", content = @Content),
    @ApiResponse(responseCode = "404", description = "영상을 찾을 수 없음", content = @Content)
  })
  @PutMapping("/{videoId}/reject")
  public ResponseEntity<VideoStatusResponse> rejectVideo(
      @Parameter(description = "영상 ID", required = true) @PathVariable UUID videoId,
      @RequestBody(required = false) VideoRejectRequest req,
      @AuthenticationPrincipal Jwt jwt) {
    String reason = req != null ? req.reason() : null;
    UUID reviewerUuid = SecurityUtils.extractUserUuid(jwt)
        .orElseThrow(() -> new IllegalArgumentException("사용자 UUID를 추출할 수 없습니다."));
    return ResponseEntity.ok(videoService.rejectVideo(videoId, reason, reviewerUuid));
  }

  @Operation(summary = "게시 (프론트 -> 백엔드) [Deprecated]", description = "[Deprecated] 게시는 2차 승인(approve) 시 자동으로 PUBLISHED 처리됩니다. 기존 API 호환을 위해 유지하되, PUBLISHED 상태가 아닌 경우 에러를 반환합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공",
      content = @Content(schema = @Schema(implementation = VideoStatusResponse.class))),
    @ApiResponse(responseCode = "400", description = "상태 변경 불가", content = @Content),
    @ApiResponse(responseCode = "404", description = "영상을 찾을 수 없음", content = @Content)
  })
  @PutMapping("/{videoId}/publish")
  public ResponseEntity<VideoStatusResponse> publishVideo(
      @Parameter(description = "영상 ID", required = true) @PathVariable UUID videoId) {
    return ResponseEntity.ok(videoService.publishVideo(videoId));
  }

  @Operation(summary = "영상 비활성화 (프론트 -> 백엔드)", description = "게시된 영상을 비활성화하여 유저에게 노출되지 않도록 합니다. (PUBLISHED → DISABLED)")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공",
      content = @Content(schema = @Schema(implementation = VideoStatusResponse.class))),
    @ApiResponse(responseCode = "400", description = "상태 변경 불가 (PUBLISHED 상태에서만 가능)", content = @Content),
    @ApiResponse(responseCode = "404", description = "영상을 찾을 수 없음", content = @Content)
  })
  @PutMapping("/{videoId}/disable")
  public ResponseEntity<VideoStatusResponse> disableVideo(
      @Parameter(description = "영상 ID", required = true) @PathVariable UUID videoId) {
    return ResponseEntity.ok(videoService.disableVideo(videoId));
  }

  @Operation(summary = "영상 활성화 (프론트 -> 백엔드)", description = "비활성화된 영상을 다시 활성화하여 유저에게 노출합니다. (DISABLED → PUBLISHED)")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공",
      content = @Content(schema = @Schema(implementation = VideoStatusResponse.class))),
    @ApiResponse(responseCode = "400", description = "상태 변경 불가 (DISABLED 상태에서만 가능)", content = @Content),
    @ApiResponse(responseCode = "404", description = "영상을 찾을 수 없음", content = @Content)
  })
  @PutMapping("/{videoId}/enable")
  public ResponseEntity<VideoStatusResponse> enableVideo(
      @Parameter(description = "영상 ID", required = true) @PathVariable UUID videoId) {
    return ResponseEntity.ok(videoService.enableVideo(videoId));
  }

  @Operation(summary = "영상 상태 강제 변경 (* 개발용)", 
      description = "어드민 테스트용: 영상 상태를 강제로 변경합니다. (상태 검증 없음)")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공",
      content = @Content(schema = @Schema(implementation = VideoStatusResponse.class))),
    @ApiResponse(responseCode = "404", description = "영상을 찾을 수 없음", content = @Content)
  })
  @PutMapping("/{videoId}/status")
  public ResponseEntity<VideoStatusResponse> forceChangeStatus(
      @Parameter(description = "영상 ID", required = true) @PathVariable UUID videoId,
      @Parameter(description = "변경할 상태", required = true) @RequestParam VideoStatus status) {
    return ResponseEntity.ok(videoService.forceChangeStatus(videoId, status.name()));
  }

  // ========================
  // 영상 메타 CRUD API
  // ========================

  @Operation(summary = "영상 목록 조회(페이징) (* 프론트 -> 백엔드)", description = "영상 메타 목록을 페이징으로 조회합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공",
      content = @Content(schema = @Schema(implementation = VideoMetaItem.class)))
  })
  @GetMapping("/list")
  public ResponseEntity<List<VideoMetaItem>> listVideos(
      @RequestParam(value = "page", defaultValue = "0") int page,
      @RequestParam(value = "size", defaultValue = "10") int size) {
    return ResponseEntity.ok(videoService.listVideoContents(page, size));
  }

  @Operation(summary = "영상 수정 (* 프론트 -> 백엔드)", description = "제목/파일 URL/버전/길이/상태/부서 목록 등을 부분 업데이트합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공",
      content = @Content(schema = @Schema(implementation = VideoMetaItem.class))),
    @ApiResponse(responseCode = "404", description = "영상을 찾을 수 없음", content = @Content)
  })
  @PutMapping("/{videoId}")
  public ResponseEntity<VideoMetaItem> updateVideo(
      @Parameter(description = "영상 ID", required = true) @PathVariable UUID videoId,
      @Valid @RequestBody VideoMetaUpdateRequest req) {
    return ResponseEntity.ok(videoService.updateVideoContent(videoId, req));
  }

  @Operation(summary = "영상 삭제 (* 프론트 -> 백엔드)", description = "영상을 삭제합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "삭제 성공"),
    @ApiResponse(responseCode = "404", description = "영상을 찾을 수 없음", content = @Content)
  })
  @DeleteMapping("/{videoId}")
  public ResponseEntity<Void> deleteVideo(
      @Parameter(description = "영상 ID", required = true) @PathVariable UUID videoId) {
    videoService.deleteVideoContent(videoId);
    return ResponseEntity.noContent().build();
  }

  // ========================
  // 검토(Review) 관련 API
  // ========================

  @Operation(summary = "검토 목록 조회 (프론트 -> 백엔드)", 
      description = "검토 대기/승인됨/반려됨 영상 목록을 조회합니다. 필터링, 정렬, 페이징을 지원합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공",
      content = @Content(schema = @Schema(implementation = ReviewQueueResponse.class)))
  })
  @GetMapping("/review-queue")
  public ResponseEntity<ReviewQueueResponse> getReviewQueue(
      @Parameter(description = "페이지 번호 (0-base)", example = "0") 
      @RequestParam(value = "page", defaultValue = "0") int page,
      @Parameter(description = "페이지 크기", example = "30") 
      @RequestParam(value = "size", defaultValue = "30") int size,
      @Parameter(description = "검색어 (제목/부서/제작자)", example = "성희롱") 
      @RequestParam(value = "search", required = false) String search,
      @Parameter(description = "내 처리만", example = "false") 
      @RequestParam(value = "myProcessingOnly", required = false) Boolean myProcessingOnly,
      @Parameter(description = "상태 필터 (pending: 검토 대기, approved: 승인됨, rejected: 반려됨)", example = "pending") 
      @RequestParam(value = "status", required = false) String statusFilter,
      @Parameter(description = "검토 단계 필터 (first: 1차, second: 2차)", example = "first") 
      @RequestParam(value = "reviewStage", required = false) String reviewStage,
      @Parameter(description = "정렬 옵션 (latest: 최신순, oldest: 오래된순, title: 제목순)", example = "latest") 
      @RequestParam(value = "sort", required = false) String sort,
      @AuthenticationPrincipal Jwt jwt) {
    UUID reviewerUuid = SecurityUtils.extractUserUuid(jwt).orElse(null);
    return ResponseEntity.ok(videoService.getReviewQueue(page, size, search, myProcessingOnly, statusFilter, reviewStage, sort, reviewerUuid));
  }

  @Operation(summary = "검토 통계 조회 (프론트 -> 백엔드)", 
      description = "검토 대기, 승인됨, 반려됨, 내 활동 개수를 조회합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공",
      content = @Content(schema = @Schema(implementation = ReviewStatsResponse.class)))
  })
  @GetMapping("/review-stats")
  public ResponseEntity<ReviewStatsResponse> getReviewStats(
      @AuthenticationPrincipal Jwt jwt) {
    UUID reviewerUuid = SecurityUtils.extractUserUuid(jwt).orElse(null);
    return ResponseEntity.ok(videoService.getReviewStats(reviewerUuid));
  }

  @Operation(summary = "영상 감사 이력 조회 (프론트 -> 백엔드)", 
      description = "특정 영상의 감사 이력(생성, 검토 요청, 승인, 반려 등)을 조회합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공",
      content = @Content(schema = @Schema(implementation = AuditHistoryResponse.class))),
    @ApiResponse(responseCode = "404", description = "영상을 찾을 수 없음", content = @Content)
  })
  @GetMapping("/{videoId}/review-history")
  public ResponseEntity<AuditHistoryResponse> getAuditHistory(
      @Parameter(description = "영상 ID", required = true) @PathVariable UUID videoId) {
    return ResponseEntity.ok(videoService.getAuditHistory(videoId));
  }

  @Operation(summary = "검토 상세 정보 조회 (프론트 -> 백엔드)", 
      description = "검토 화면에서 필요한 영상의 상세 정보를 조회합니다. (영상 정보, 자동 점검 결과 등)")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공",
      content = @Content(schema = @Schema(implementation = ReviewDetailResponse.class))),
    @ApiResponse(responseCode = "404", description = "영상을 찾을 수 없음", content = @Content)
  })
  @GetMapping("/{videoId}/review-detail")
  public ResponseEntity<ReviewDetailResponse> getReviewDetail(
      @Parameter(description = "영상 ID", required = true) @PathVariable UUID videoId) {
    return ResponseEntity.ok(videoService.getReviewDetail(videoId));
  }
}
