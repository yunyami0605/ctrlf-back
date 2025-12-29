package com.ctrlf.infra.rag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * RAG 관련 요청/응답 DTO 모음.
 * 인프라 서비스 내 임시 배치이므로, 나중에 rag-service로 이동하기 쉽게
 * 독립 패키지와 DTO로 구성합니다.
 */
public final class RagDtos {
    private RagDtos() {}

    // ---------- Presign ----------
    /**
     * 문서 업로드용 Presigned URL 발급 요청.
     * - filename: 원본 파일명
     * - contentType: MIME 타입
     * type은 항상 "docs"로 고정합니다.
     */
    @Getter
    @NoArgsConstructor
    public static class PresignRequest {
        @NotBlank
        @Schema(example = "mydoc.pdf")
        private String filename;

        @NotBlank
        @Schema(example = "application/pdf")
        private String contentType;
    }

    /**
     * Presigned URL 발급 응답.
     * - uploadUrl: PUT 업로드용 URL
     * - fileUrl: 업로드 완료 후 보관되는 s3://bucket/key
     */
    @Getter
    @AllArgsConstructor
    public static class PresignResponse {
        private String uploadUrl;
        private String fileUrl;
    }

    // ---------- Upload Document ----------
    /**
     * 문서 업로드 메타 등록 요청 DTO.
     * presign을 이용해 S3 업로드가 끝난 후 메타 정보를 저장할 때 사용합니다.
     * uploaderUuid는 JWT 토큰에서 자동으로 추출되므로 요청에 포함하지 않습니다.
     */
    @Getter
    @NoArgsConstructor
    public static class UploadRequest {
        @NotBlank
        @Schema(example = "산업안전 규정집 v3")
        private String title;

        @NotBlank
        @Schema(example = "HR")
        private String domain;

        @NotBlank
        @Schema(example = "s3://ctrl-s3/docs/file.pdf")
        private String fileUrl;
    }

    /**
     * 문서 업로드 메타 등록 응답 DTO.
     */
    @Getter
    @AllArgsConstructor
    public static class UploadResponse {
        private String documentId; // UUID string
        private String status;     // "QUEUED"
        private String createdAt;  // ISO-8601
    }

    // ---------- List Documents ----------
    /**
     * 문서 목록 응답 항목 DTO.
     */
    @Getter
    @AllArgsConstructor
    public static class DocumentListItem {
        private String id;
        private String title;
        private String domain;
        private String uploaderUuid;
        private String createdAt;
    }

    // ---------- Update Document ----------
    @Getter
    @NoArgsConstructor
    public static class UpdateRequest {
        @Schema(example = "산업안전 규정집 v4", nullable = true)
        private String title;
        @Schema(example = "HR", nullable = true)
        private String domain;
        @Schema(example = "s3://ctrl-s3/docs/file_v4.pdf", nullable = true)
        private String fileUrl;
    }

    @Getter
    @AllArgsConstructor
    public static class UpdateResponse {
        private String documentId;
        private String status;   // REPROCESSING
        private String updatedAt;
    }

    // ---------- Delete Document ----------
    @Getter
    @AllArgsConstructor
    public static class DeleteResponse {
        private String documentId;
        private String status;   // DELETED
        private String deletedAt;
    }

    // ---------- Reprocess Document ----------
    @Getter
    @NoArgsConstructor
    public static class ReprocessRequest {
        @Schema(example = "산업안전 규정집 v4", nullable = true)
        private String title;
        @Schema(example = "HR", nullable = true)
        private String domain;
        @Schema(example = "s3://ctrl-s3/docs/new.pdf", nullable = true)
        private String fileUrl;
        @Schema(example = "c13c91f2-fb1a-4d42-b381-72847a52fb99", nullable = true)
        private String requestedBy;
    }

    @Getter
    @AllArgsConstructor
    public static class ReprocessResponse {
        private String documentId;
        private boolean accepted;
        private String status;    // REPROCESSING
        private String jobId;
        private String updatedAt;
    }

    // ---------- Document Status ----------
    /**
     * 문서 처리 상태 조회 응답 DTO.
     */
    @Getter
    @AllArgsConstructor
    public static class DocumentStatusResponse {
        private String documentId;
        private String status;      // QUEUED, PROCESSING, COMPLETED, FAILED
        private String createdAt;
        private String processedAt; // null if not processed yet
    }

    // ---------- Document Text ----------
    /**
     * 문서 원문 텍스트 조회 응답 DTO.
     */
    @Getter
    @AllArgsConstructor
    public static class DocumentTextResponse {
        private String documentId;
        private String text;        // 추출된 원문 텍스트
    }

    // ---------- Document Info ----------
    /**
     * 문서 정보 조회 응답 DTO.
     */
    @Getter
    @AllArgsConstructor
    public static class DocumentInfoResponse {
        private String id;
        private String title;
        private String domain;
        private String sourceUrl;   // fileUrl
        private String status;
    }

    // ---------- Internal API: Chunks Bulk Upsert ----------
    /**
     * 문서 청크 Bulk Upsert 요청 (내부 API - FastAPI → Spring).
     */
    @Getter
    @NoArgsConstructor
    public static class ChunksBulkUpsertRequest {
        @Schema(description = "청크 리스트", required = true)
        @jakarta.validation.constraints.NotNull
        private java.util.List<ChunkItem> chunks;

        @Schema(description = "멱등 키")
        private String requestId;
    }

    @Getter
    @NoArgsConstructor
    public static class ChunkItem {
        @Schema(description = "청크 번호", required = true)
        @jakarta.validation.constraints.NotNull
        private Integer chunkIndex;

        @Schema(description = "청크 텍스트", required = true)
        @NotBlank
        private String chunkText;

        @Schema(description = "메타데이터 (권장)")
        private Object chunkMeta;
    }

    @Getter
    @AllArgsConstructor
    public static class ChunksBulkUpsertResponse {
        private boolean saved;
        private int savedCount;
    }

    // ---------- Internal API: Fail Chunks Bulk Upsert ----------
    /**
     * 임베딩 실패 로그 Bulk Upsert 요청 (내부 API - FastAPI → Spring).
     */
    @Getter
    @NoArgsConstructor
    public static class FailChunksBulkUpsertRequest {
        @Schema(description = "실패 청크 리스트", required = true)
        @jakarta.validation.constraints.NotNull
        private java.util.List<FailChunkItem> fails;

        @Schema(description = "멱등 키")
        private String requestId;
    }

    @Getter
    @NoArgsConstructor
    public static class FailChunkItem {
        @Schema(description = "청크 번호", required = true)
        @jakarta.validation.constraints.NotNull
        private Integer chunkIndex;

        @Schema(description = "실패 사유", required = true)
        @NotBlank
        private String failReason;
    }

    @Getter
    @AllArgsConstructor
    public static class FailChunksBulkUpsertResponse {
        private boolean saved;
        private int savedCount;
    }

    // ---------- Policy Management ----------
    /**
     * 사규 목록 조회 응답 DTO.
     */
    @Getter
    @AllArgsConstructor
    public static class PolicyListItem {
        private String documentId;
        private String title;
        private String domain;
        private java.util.List<VersionSummary> versions;
        private int totalVersions;
    }

    @Getter
    @AllArgsConstructor
    public static class VersionSummary {
        private Integer version;
        private String status;
        private String createdAt;
    }

    /**
     * 사규 상세 조회 응답 DTO (document_id 기준).
     */
    @Getter
    @AllArgsConstructor
    public static class PolicyDetailResponse {
        private String documentId;
        private String title;
        private String domain;
        private java.util.List<VersionDetail> versions;
    }

    /**
     * 버전별 상세 조회 응답 DTO.
     */
    @Getter
    @AllArgsConstructor
    public static class VersionDetail {
        private String id; // UUID
        private String documentId;
        private String title;
        private String domain;
        private Integer version;
        private String status;
        private String changeSummary;
        private String sourceUrl;
        private String uploaderUuid;
        private String createdAt;
        private String processedAt;
    }

    /**
     * 새 사규 생성 요청 DTO.
     */
    @Getter
    @NoArgsConstructor
    public static class CreatePolicyRequest {
        @NotBlank
        @Schema(example = "POL-EDU-015")
        private String documentId;

        @NotBlank
        @Schema(example = "교육/퀴즈 운영 정책")
        private String title;

        @NotBlank
        @Schema(example = "EDU")
        private String domain;

        @Schema(example = "s3://ctrl-s3/docs/hr_safety_v3.pdf")
        private String fileUrl;

        @Schema(example = "초기 사규 등록")
        private String changeSummary;
    }

    /**
     * 새 사규 생성 응답 DTO.
     */
    @Getter
    @AllArgsConstructor
    public static class CreatePolicyResponse {
        private String id; // UUID
        private String documentId;
        private String title;
        private Integer version;
        private String status;
        private String createdAt;
    }

    /**
     * 새 버전 생성 요청 DTO.
     */
    @Getter
    @NoArgsConstructor
    public static class CreateVersionRequest {
        @Schema(example = "s3://ctrl-s3/docs/policy_v2.pdf")
        private String fileUrl;

        @Schema(example = "퀴즈 리포트 및 배포 캘린더 추가")
        private String changeSummary;
    }

    /**
     * 새 버전 생성 응답 DTO.
     */
    @Getter
    @AllArgsConstructor
    public static class CreateVersionResponse {
        private String id; // UUID
        private String documentId;
        private Integer version;
        private String status;
        private String createdAt;
    }

    /**
     * 버전 수정 요청 DTO.
     */
    @Getter
    @NoArgsConstructor
    public static class UpdateVersionRequest {
        @Schema(example = "퀴즈 리포트(오답 분석/재학습) 및 배포 캘린더 추가(초안)")
        private String changeSummary;

        @Schema(example = "s3://ctrl-s3/docs/policy_v2_updated.pdf")
        private String fileUrl;
    }

    /**
     * 버전 수정 응답 DTO.
     */
    @Getter
    @AllArgsConstructor
    public static class UpdateVersionResponse {
        private String id;
        private String documentId;
        private Integer version;
        private String status;
        private String updatedAt;
    }

    /**
     * 상태 변경 요청 DTO.
     */
    @Getter
    @NoArgsConstructor
    public static class UpdateStatusRequest {
        @NotBlank
        @Schema(example = "ACTIVE", description = "ACTIVE, DRAFT, PENDING, ARCHIVED")
        private String status;
    }

    /**
     * 상태 변경 응답 DTO.
     */
    @Getter
    @AllArgsConstructor
    public static class UpdateStatusResponse {
        private String id;
        private String documentId;
        private Integer version;
        private String status;
        private String updatedAt;
    }

    /**
     * 파일 업로드/교체 요청 DTO.
     */
    @Getter
    @NoArgsConstructor
    public static class ReplaceFileRequest {
        @NotBlank
        @Schema(example = "s3://ctrl-s3/docs/policy_v2_new.pdf")
        private String fileUrl;
    }

    /**
     * 파일 업로드/교체 응답 DTO.
     */
    @Getter
    @AllArgsConstructor
    public static class ReplaceFileResponse {
        private String id;
        private String documentId;
        private Integer version;
        private String sourceUrl;
        private String updatedAt;
    }
}

