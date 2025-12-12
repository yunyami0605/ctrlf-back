package com.ctrlf.infra.rag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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
        @Schema(example = "c13c91f2-fb1a-4d42-b381-72847a52fb99")
        private String uploaderUuid;

        @NotBlank
        @Schema(example = "s3://bucket/docs/file.pdf")
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
        @Schema(example = "s3://bucket/docs/file_v4.pdf", nullable = true)
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
        @Schema(example = "s3://bucket/docs/new.pdf", nullable = true)
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
}

