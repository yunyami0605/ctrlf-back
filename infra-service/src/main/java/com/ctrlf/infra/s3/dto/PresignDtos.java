package com.ctrlf.infra.s3.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * S3 Presign API 요청/응답 DTO 모음.
 */
public final class PresignDtos {
    private PresignDtos() {}

    /**
     * 업로드 URL 발급 요청.
     * - filename: 원본 파일명
     * - contentType: MIME 타입
     * - type: 파일 카테고리(경로 prefix)
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class S3UploadRequest {
        @NotBlank
        @JsonProperty("fileName") // 프론트엔드가 camelCase로 보내므로 매핑
        @Schema(example = "test.png")
        private String filename;
        @NotBlank
        @Schema(example = "image/png")
        private String contentType;
        @NotBlank
        @Pattern(regexp = "^(image|docs|video)$", message = "type must be one of: image, docs, video")
        @Schema(allowableValues = {"image","docs","video"}, example = "image")
        private String type;
    }

    /**
     * 업로드 URL 발급 응답.
     * - uploadUrl: 클라이언트가 PUT 업로드할 Presigned URL
     * - fileUrl: 업로드 완료 후 저장될 s3://bucket/key
     */
    @Getter
    @AllArgsConstructor
    public static class UploadResponse {
        private String uploadUrl;
        private String fileUrl;
    }

    /**
     * 다운로드 URL 발급 요청.
     * - fileUrl: s3://bucket/key 또는 key 문자열
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class DownloadRequest {
        @NotBlank
        private String fileUrl; // s3://bucket/key
    }

    /**
     * 다운로드 URL 발급 응답.
     * - downloadUrl: GET으로 접근 가능한 Presigned URL
     */
    @Getter
    @AllArgsConstructor
    public static class DownloadResponse {
        private String downloadUrl;
    }
}

