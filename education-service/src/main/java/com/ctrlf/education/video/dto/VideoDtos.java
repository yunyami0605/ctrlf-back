package com.ctrlf.education.video.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * 영상 생성 관련 API의 요청/응답 DTO 모음.
 */
public final class VideoDtos {

    private VideoDtos() {}

    // ========================
    // 영상 생성 Job 관련 DTOs
    // ========================

    /**
     * 영상 생성 요청.
     */
    @Schema(description = "영상 생성 요청")
    public record VideoJobRequest(
        @Schema(description = "교육 ID", example = "550e8400-e29b-41d4-a716-446655440000")
        @NotNull(message = "eduId는 필수입니다")
        UUID eduId,

        @Schema(description = "최종 스크립트 ID", example = "550e8400-e29b-41d4-a716-446655440001")
        @NotNull(message = "scriptId는 필수입니다")
        UUID scriptId,

        @Schema(description = "영상 컨텐츠 ID", example = "550e8400-e29b-41d4-a716-446655440002")
        @NotNull(message = "videoId는 필수입니다")
        UUID videoId
    ) {}

    /**
     * 영상 생성 요청 응답.
     */
    @Schema(description = "영상 생성 요청 응답")
    public record VideoJobResponse(
        @Schema(description = "생성된 Job ID", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID jobId,

        @Schema(description = "Job 상태", example = "QUEUED")
        String status
    ) {}

    /**
     * 영상 재시도 응답.
     */
    @Schema(description = "영상 재시도 응답")
    public record VideoRetryResponse(
        @Schema(description = "Job ID", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID jobId,

        @Schema(description = "재시도 후 상태", example = "QUEUED")
        String status,

        @Schema(description = "누적 재시도 횟수", example = "2")
        Integer retryCount
    ) {}

    /**
     * AI 서버 → 백엔드: 영상 생성 완료 콜백 요청.
     */
    @Schema(description = "영상 생성 완료 콜백 요청 (AI 서버 → 백엔드)")
    public record VideoCompleteCallback(
        @Schema(description = "Job ID", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID jobId,

        @Schema(description = "생성된 영상 URL", example = "https://cdn.com/video.mp4")
        String videoUrl,

        @Schema(description = "영상 길이(초)", example = "1230")
        Integer duration,

        @Schema(description = "완료 상태", example = "COMPLETED")
        @NotBlank(message = "status는 필수입니다")
        String status
    ) {}

    /**
     * 영상 생성 완료 콜백 응답.
     */
    @Schema(description = "영상 생성 완료 콜백 응답")
    public record VideoCompleteResponse(
        @Schema(description = "저장 성공 여부", example = "true")
        boolean saved
    ) {}

    // ========================
    // 백엔드 → AI 서버 요청용 내부 DTOs
    // ========================

    /**
     * 클라이언트 → 백엔드: 전처리/임베딩/스크립트 생성 시작 요청.
     * materialId는 PathVariable로 받고, 바디에는 eduId와 fileUrl만 받습니다.
     */
    @Schema(description = "전처리/임베딩/스크립트 생성 시작 요청 (클라이언트 → 백엔드)")
    public record MaterialProcessStartRequest(
        @Schema(description = "교육 ID", example = "550e8400-e29b-41d4-a716-446655440000")
        @NotNull(message = "eduId는 필수입니다")
        UUID eduId,

        @Schema(description = "S3 파일 URL", example = "s3://ctrl-s3/docs/file.pdf")
        @NotBlank(message = "fileUrl은 필수입니다")
        String fileUrl
    ) {}

    /**
     * 백엔드 → AI 서버: 전처리 + 임베딩 + 스크립트 생성 요청.
     */
    @Schema(description = "전처리/임베딩/스크립트 생성 요청 (백엔드 → AI 서버)")
    public record MaterialProcessRequest(
        @Schema(description = "자료 ID")
        UUID materialId,

        @Schema(description = "교육 ID")
        UUID eduId,

        @Schema(description = "파일 URL (S3)")
        String fileUrl
    ) {}

    /**
     * AI 서버 응답: 처리 요청 수신 확인.
     */
    @Schema(description = "AI 서버 처리 요청 응답")
    public record AiProcessResponse(
        @Schema(description = "요청 수신 여부", example = "true")
        boolean received,

        @Schema(description = "처리 상태", example = "PROCESSING")
        String status
    ) {}

    /**
     * 백엔드 → AI 서버: 영상 재생성 요청.
     */
    @Schema(description = "영상 재생성 요청 (백엔드 → AI 서버)")
    public record VideoRetryRequest(
        @Schema(description = "Job ID")
        UUID jobId,

        @Schema(description = "스크립트 ID")
        UUID scriptId,

        @Schema(description = "교육 ID")
        UUID eduId,

        @Schema(description = "재시도 여부")
        boolean retry
    ) {}

    /**
     * AI 서버 응답: 영상 생성/재생성 요청 수신 확인.
     */
    @Schema(description = "AI 서버 영상 요청 응답")
    public record AiVideoResponse(
        @Schema(description = "Job ID", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID jobId,

        @Schema(description = "요청 수락 여부", example = "true")
        boolean accepted,

        @Schema(description = "상태", example = "QUEUED")
        String status
    ) {}

    /**
     * 백엔드 → AI 서버: 영상 렌더 생성 요청.
     */
    @Schema(description = "영상 렌더 생성 요청 (백엔드 → AI 서버)")
    public record RenderJobRequest(
        @Schema(description = "렌더 Job ID(백 발급)")
        UUID jobId,

        @Schema(description = "영상 ID")
        String videoId,

        @Schema(description = "승인된 스크립트 ID")
        UUID scriptId,

        @Schema(description = "승인 버전(스냅샷 고정)")
        Integer scriptVersion,

        @Schema(description = "렌더 정책 프리셋")
        String renderPolicyId,

        @Schema(description = "멱등 키(권장)")
        UUID requestId
    ) {}

    /**
     * AI 서버 응답: 렌더 생성 요청 수신 확인.
     */
    @Schema(description = "AI 서버 렌더 요청 응답")
    public record RenderJobResponse(
        @Schema(description = "요청 수신 여부", example = "true")
        boolean received,

        @Schema(description = "Job ID", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID jobId,

        @Schema(description = "상태", example = "PROCESSING")
        String status
    ) {}

    // ---------- Video Job management (list/detail/update) ----------
    @Schema(description = "영상 생성 Job 요약")
    public record JobItem(
        @Schema(description = "Job ID") UUID jobId,
        @Schema(description = "스크립트 ID") UUID scriptId,
        @Schema(description = "교육 ID") UUID eduId,
        @Schema(description = "영상 ID (생성된 경우)") UUID videoId,
        @Schema(description = "상태") String status,
        @Schema(description = "재시도 횟수") Integer retryCount,
        @Schema(description = "생성된 영상 URL") String videoUrl,
        @Schema(description = "영상 길이(초)") Integer duration,
        @Schema(description = "생성 시각 (ISO8601)") String createdAt,
        @Schema(description = "수정 시각 (ISO8601)") String updatedAt,
        @Schema(description = "실패 사유") String failReason
    ) {}

    @Schema(description = "영상 생성 Job 수정 요청")
    public record VideoJobUpdateRequest(
        @Schema(description = "새 상태", example = "CANCELLED") String status,
        @Schema(description = "실패 사유 또는 메모", example = "수동 취소/오류 메시지 등") String failReason,
        @Schema(description = "생성된 영상 URL(수정 필요시)", example = "https://cdn.example.com/video.mp4") String videoUrl,
        @Schema(description = "영상 길이(초)", example = "120") Integer duration
    ) {}

    // ========================
    // 영상 메타 CRUD DTOs (ADMIN)
    // ========================

    @Schema(description = "영상 메타 정보")
    public record VideoMetaItem(
        @Schema(description = "영상 ID") UUID id,
        @Schema(description = "교육 ID") UUID educationId,
        @Schema(description = "영상 제목") String title,
        @Schema(description = "생성 Job ID") UUID generationJobId,
        @Schema(description = "스크립트 ID") UUID scriptId,
        @Schema(description = "파일 URL") String fileUrl,
        @Schema(description = "버전") Integer version,
        @Schema(description = "길이(초)") Integer duration,
        @Schema(description = "상태") VideoStatus status,
        @Schema(description = "수강 가능 부서 목록 (사용 가능한 값: '전체 부서', '총무팀', '기획팀', '마케팅팀', '인사팀', '재무팀', '개발팀', '영업팀', '법무팀')", 
                example = "[\"총무팀\", \"기획팀\"]") List<String> departmentScope,
        @Schema(description = "재생 순서(0-base)") Integer orderIndex,
        @Schema(description = "생성시각 ISO8601") String createdAt
    ) {}

    @Schema(description = "영상 메타 수정 요청 (부분 업데이트)")
    public record VideoMetaUpdateRequest(
        @Schema(description = "영상 제목") String title,
        @Schema(description = "파일 URL") String fileUrl,
        @Schema(description = "버전") Integer version,
        @Schema(description = "길이(초)") Integer duration,
        @Schema(description = "상태") VideoStatus status,
        @Schema(description = "재생 순서(0-base)") Integer orderIndex
    ) {}

    // ========================
    // 영상 컨텐츠 관리 DTOs (ADMIN)
    // ========================

    @Schema(description = "영상 컨텐츠 생성 요청")
    public record VideoCreateRequest(
        @Schema(description = "교육 ID", example = "550e8400-e29b-41d4-a716-446655440000")
        @NotNull(message = "educationId는 필수입니다")
        UUID educationId,

        @Schema(description = "영상 제목", example = "2024년 성희롱 예방 교육")
        @NotBlank(message = "title은 필수입니다")
        String title
    ) {}

    @Schema(description = "영상 컨텐츠 생성 응답")
    public record VideoCreateResponse(
        @Schema(description = "생성된 영상 ID") UUID videoId,
        @Schema(description = "상태") String status
    ) {}

    @Schema(description = "영상 상태 변경 응답")
    public record VideoStatusResponse(
        @Schema(description = "영상 ID") UUID videoId,
        @Schema(description = "이전 상태") String previousStatus,
        @Schema(description = "현재 상태") String currentStatus,
        @Schema(description = "변경 시각") String updatedAt
    ) {}

    @Schema(description = "검토 반려 요청")
    public record VideoRejectRequest(
        @Schema(description = "반려 사유", example = "스크립트 내용 수정 필요")
        String reason
    ) {}

    // ========================
    // 영상 상태 Enum (어드민 테스트용)
    // ========================

    @Schema(description = "영상 상태")
    public enum VideoStatus {
        DRAFT,                      // 초기 생성
        SCRIPT_GENERATING,          // 스크립트 생성 중
        SCRIPT_READY,               // 스크립트 생성 완료
        SCRIPT_REVIEW_REQUESTED,    // 1차 검토 요청 (스크립트)
        SCRIPT_APPROVED,            // 1차 승인 (영상 생성 가능)
        PROCESSING,                 // 영상 생성 중
        READY,                      // 영상 생성 완료
        FINAL_REVIEW_REQUESTED,     // 2차 검토 요청 (영상)
        PUBLISHED,                  // 최종 승인/게시 (유저 노출)
        DISABLED                    // 비활성화 (유저 노출 중지)
    }

    @Schema(description = "영상 상태 강제 변경 요청 (어드민 테스트용)")
    public record VideoStatusChangeRequest(
        @Schema(description = "변경할 상태", example = "READY")
        @NotNull(message = "status는 필수입니다")
        VideoStatus status
    ) {}

    // ========================
    // 소스셋(SourceSet) 관련 DTOs
    // ========================

    /**
     * 소스셋 생성 요청.
     */
    @Schema(description = "소스셋 생성 요청")
    public record SourceSetCreateRequest(
        @Schema(description = "소스셋 제목", example = "직장내괴롭힘 통합 교육자료")
        @NotBlank(message = "title은 필수입니다")
        String title,

        @Schema(description = "소스셋 도메인", example = "FOUR_MANDATORY")
        String domain,

        @Schema(description = "포함할 문서 ID 목록")
        @NotNull(message = "documentIds는 필수입니다")
        java.util.List<String> documentIds,

        @Schema(description = "연결된 교육 ID", example = "550e8400-e29b-41d4-a716-446655440000")
        @NotNull(message = "educationId는 필수입니다")
        UUID educationId,

        @Schema(description = "연결된 영상 ID", example = "550e8400-e29b-41d4-a716-446655440001")
        @NotNull(message = "videoId는 필수입니다")
        UUID videoId
        // requestedBy는 JWT 토큰에서 자동으로 추출되므로 요청에 포함하지 않습니다.
    ) {}

    /**
     * 소스셋 생성 응답.
     */
    @Schema(description = "소스셋 생성 응답")
    public record SourceSetCreateResponse(
        @Schema(description = "소스셋 ID", example = "SS-001")
        String sourceSetId,

        @Schema(description = "상태", example = "CREATED")
        String status,

        @Schema(description = "포함된 문서 ID 목록")
        java.util.List<String> documentIds
    ) {}

    /**
     * 소스셋 문서 변경 요청.
     */
    @Schema(description = "소스셋 문서 변경 요청")
    public record SourceSetUpdateRequest(
        @Schema(description = "추가할 문서 IDs")
        java.util.List<String> addDocumentIds,

        @Schema(description = "제거할 문서 IDs")
        java.util.List<String> removeDocumentIds,

        @Schema(description = "변경 사유")
        String comment
    ) {}

    // ========================
    // 내부 API DTOs (FastAPI ↔ Spring)
    // ========================

    /**
     * 소스셋 문서 목록 조회 응답 (내부 API).
     */
    @Schema(description = "소스셋 문서 목록 조회 응답 (내부 API)")
    public record InternalSourceSetDocumentsResponse(
        @Schema(description = "소스셋 ID") String sourceSetId,
        @Schema(description = "문서 목록") java.util.List<InternalDocumentItem> documents
    ) {
        @Schema(description = "문서 정보")
        public record InternalDocumentItem(
            @Schema(description = "문서 ID") String documentId,
            @Schema(description = "제목") String title,
            @Schema(description = "도메인") String domain,
            @Schema(description = "원본 파일 URL") String sourceUrl,
            @Schema(description = "상태") String status
        ) {}
    }

    /**
     * 스크립트 패치 (씬 단위 업서트).
     * 씬이 생성될 때마다 백엔드에 전송하여 부분 저장.
     */
    @Schema(description = "스크립트 패치 (씬 단위 업서트)")
    public record ScriptPatch(
        @Schema(description = "패치 타입 (SCENE_UPSERT)") String type,
        @Schema(description = "스크립트 ID") String scriptId,
        @Schema(description = "챕터 인덱스 (0-based)") Integer chapterIndex,
        @Schema(description = "챕터 제목") String chapterTitle,
        @Schema(description = "씬 인덱스 (0-based)") Integer sceneIndex,
        @Schema(description = "씬 데이터") SourceSetCompleteCallback.SourceSetScript.SourceSetScene scene,
        @Schema(description = "전체 씬 수") Integer totalScenes,
        @Schema(description = "현재 씬 번호 (1-based)") Integer currentScene
    ) {}

    /**
     * 소스셋 완료 콜백 요청 (FastAPI → Spring).
     *
     * 두 가지 모드 지원:
     * 1. 전체 스크립트 전송: script 필드 사용, sourceSetStatus=SCRIPT_READY
     * 2. 씬별 패치 전송: scriptPatch 필드 사용, sourceSetStatus=SCRIPT_GENERATING
     */
    @Schema(description = "소스셋 완료 콜백 요청 (FastAPI → Spring)")
    public record SourceSetCompleteCallback(
        @Schema(description = "영상 ID", required = true) @NotNull UUID videoId,
        @Schema(description = "결과 상태 (COMPLETED | FAILED)", required = true) @NotBlank String status,
        @Schema(description = "DB source_set 상태 (SCRIPT_GENERATING | SCRIPT_READY | FAILED)", required = true) @NotBlank String sourceSetStatus,
        @Schema(description = "문서별 결과", required = true) @NotNull java.util.List<DocumentResult> documents,
        @Schema(description = "생성된 스크립트 (전체 전송 시)") SourceSetScript script,
        @Schema(description = "스크립트 패치 (씬별 전송 시)") ScriptPatch scriptPatch,
        @Schema(description = "실패 코드") String errorCode,
        @Schema(description = "실패 메시지") String errorMessage,
        @Schema(description = "멱등 키") UUID requestId,
        @Schema(description = "추적용") String traceId
    ) {
        @Schema(description = "문서별 결과")
        public record DocumentResult(
            @Schema(description = "문서 ID") String documentId,
            @Schema(description = "상태 (COMPLETED | FAILED)") String status,
            @Schema(description = "실패 사유") String failReason
        ) {}

        @Schema(description = "생성된 스크립트 (성공 시)")
        public record SourceSetScript(
            @Schema(description = "스크립트 ID (FastAPI 생성)") String scriptId,
            @Schema(description = "교육 ID") String educationId,
            @Schema(description = "소스셋 ID") String sourceSetId,
            @Schema(description = "제목") String title,
            @Schema(description = "총 길이(초)") Integer totalDurationSec,
            @Schema(description = "버전") Integer version,
            @Schema(description = "LLM 모델") String llmModel,
            @Schema(description = "챕터 목록") java.util.List<SourceSetChapter> chapters
        ) {
            @Schema(description = "챕터")
            public record SourceSetChapter(
                @Schema(description = "챕터 인덱스") Integer chapterIndex,
                @Schema(description = "제목") String title,
                @Schema(description = "길이(초)") Integer durationSec,
                @Schema(description = "씬 목록") java.util.List<SourceSetScene> scenes
            ) {}

            @Schema(description = "씬")
            public record SourceSetScene(
                @Schema(description = "씬 인덱스") Integer sceneIndex,
                @Schema(description = "목적") String purpose,
                @Schema(description = "내레이션") String narration,
                @Schema(description = "자막") String caption,
                @Schema(description = "시각 연출") String visual,
                @Schema(description = "길이(초)") Integer durationSec,
                @Schema(description = "신뢰도") Float confidenceScore,
                @Schema(description = "출처 참조 (멀티문서)") java.util.List<SourceRef> sourceRefs
            ) {
                @Schema(description = "출처 참조")
                public record SourceRef(
                    @Schema(description = "문서 ID") String documentId,
                    @Schema(description = "청크 인덱스") Integer chunkIndex
                ) {}
            }
        }
    }

    /**
     * 소스셋 완료 콜백 응답.
     */
    @Schema(description = "소스셋 완료 콜백 응답")
    public record SourceSetCompleteResponse(
        @Schema(description = "저장 성공 여부") boolean saved,
        @Schema(description = "생성된 스크립트 ID (저장 성공 시)") UUID scriptId
    ) {}

    // ========================
    // 검토(Review) 관련 DTOs
    // ========================

    @Schema(description = "검토 대기 목록 조회 응답")
    public record ReviewQueueItem(
        @Schema(description = "영상 ID") UUID videoId,
        @Schema(description = "교육 ID") UUID educationId,
        @Schema(description = "교육 제목") String educationTitle,
        @Schema(description = "영상 제목") String videoTitle,
        @Schema(description = "상태") VideoStatus status,
        @Schema(description = "검토 단계 (1차: SCRIPT_REVIEW_REQUESTED, 2차: FINAL_REVIEW_REQUESTED)") String reviewStage,
        @Schema(description = "제작자 부서") String creatorDepartment,
        @Schema(description = "제작자 이름") String creatorName,
        @Schema(description = "제작자 UUID") UUID creatorUuid,
        @Schema(description = "제출 시각") String submittedAt,
        @Schema(description = "카테고리") String category,
        @Schema(description = "교육 유형") String eduType
    ) {}

    @Schema(description = "검토 대기 목록 조회 페이징 응답")
    public record ReviewQueueResponse(
        @Schema(description = "검토 대기 목록") List<ReviewQueueItem> items,
        @Schema(description = "전체 개수") Long totalCount,
        @Schema(description = "현재 페이지 (0-base)") int page,
        @Schema(description = "페이지 크기") int size,
        @Schema(description = "전체 페이지 수") int totalPages,
        @Schema(description = "1차 검토 대기 개수") Long firstRoundCount,
        @Schema(description = "2차 검토 대기 개수") Long secondRoundCount,
        @Schema(description = "문서 타입 개수") Long documentCount
    ) {}

    @Schema(description = "검토 통계 응답")
    public record ReviewStatsResponse(
        @Schema(description = "검토 대기 개수") Long pendingCount,
        @Schema(description = "승인됨 개수") Long approvedCount,
        @Schema(description = "반려됨 개수") Long rejectedCount,
        @Schema(description = "내 활동 개수") Long myActivityCount
    ) {}

    @Schema(description = "감사 이력 항목")
    public record AuditHistoryItem(
        @Schema(description = "이벤트 타입") String eventType,
        @Schema(description = "이벤트 설명") String description,
        @Schema(description = "발생 시각") String timestamp,
        @Schema(description = "처리자 이름") String actorName,
        @Schema(description = "처리자 UUID") UUID actorUuid,
        @Schema(description = "반려 사유 (반려인 경우)") String rejectionReason,
        @Schema(description = "반려 단계 (반려인 경우)") String rejectionStage
    ) {}

    @Schema(description = "감사 이력 조회 응답")
    public record AuditHistoryResponse(
        @Schema(description = "영상 ID") UUID videoId,
        @Schema(description = "영상 제목") String videoTitle,
        @Schema(description = "스크립트 ID") UUID scriptId,
        @Schema(description = "감사 이력 목록") List<AuditHistoryItem> history
    ) {}

    @Schema(description = "검토 상세 정보 응답")
    public record ReviewDetailResponse(
        @Schema(description = "영상 ID") UUID videoId,
        @Schema(description = "교육 ID") UUID educationId,
        @Schema(description = "교육 제목") String educationTitle,
        @Schema(description = "영상 제목") String videoTitle,
        @Schema(description = "상태") VideoStatus status,
        @Schema(description = "검토 단계") String reviewStage,
        @Schema(description = "제작자 부서") String creatorDepartment,
        @Schema(description = "제작자 이름") String creatorName,
        @Schema(description = "제작자 UUID") UUID creatorUuid,
        @Schema(description = "제출 시각") String submittedAt,
        @Schema(description = "업데이트 시각") String updatedAt,
        @Schema(description = "카테고리") String category,
        @Schema(description = "교육 유형") String eduType,
        @Schema(description = "스크립트 ID") UUID scriptId,
        @Schema(description = "스크립트 버전") Integer scriptVersion
    ) {}

    // ========================
    // 마지막 시청 영상 조회 DTOs (Q4 이어보기용)
    // ========================

    @Schema(description = "마지막 시청 영상 정보 (이어보기용)")
    public record LastVideoProgressResponse(
        @Schema(description = "교육 ID") String education_id,
        @Schema(description = "영상 ID") String video_id,
        @Schema(description = "교육 제목") String education_title,
        @Schema(description = "영상 제목") String video_title,
        @Schema(description = "마지막 시청 위치(초)") Integer resume_position_seconds,
        @Schema(description = "진행률(%)") Integer progress_percent,
        @Schema(description = "영상 전체 길이(초)") Integer duration
    ) {}
}
