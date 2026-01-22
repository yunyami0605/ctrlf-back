package com.ctrlf.infra.ailog.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * AI 로그 관련 DTO
 */
public final class AiLogDtos {
    private AiLogDtos() {}

    /**
     * AI 로그 Bulk 수신 요청
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BulkRequest {
        @NotEmpty
        private List<LogItem> logs;
    }

    /**
     * AI 로그 항목
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LogItem {
        @NotNull
        private Instant createdAt;

        @NotNull
        private String userId;

        private String userRole;
        private String department;
        private String domain;
        private String route;
        private String modelName;
        private Boolean hasPiiInput;
        private Boolean hasPiiOutput;
        private Boolean ragUsed;
        private Integer ragSourceCount;
        private Long latencyMsTotal;
        private String errorCode;
        private String traceId;
        private String conversationId;
        private Integer turnId;
    }

    /**
     * AI 로그 Bulk 수신 응답
     */
    @Getter
    @AllArgsConstructor
    public static class BulkResponse {
        private Integer received;
        private Integer saved;
        private Integer failed;
        private List<ErrorItem> errors;
    }

    /**
     * 에러 항목
     */
    @Getter
    @AllArgsConstructor
    public static class ErrorItem {
        private Integer index;
        private String errorCode;
        private String message;
    }

    /**
     * 관리자 대시보드 로그 목록 조회 응답
     */
    @Getter
    @AllArgsConstructor
    public static class LogListItem {
        private UUID id;
        private String createdAt;
        private String userId;
        private String userRole;
        private String department;
        private String domain;
        private String route;
        private String modelName;
        private Boolean hasPiiInput;
        private Boolean hasPiiOutput;
        private Boolean ragUsed;
        private Integer ragSourceCount;
        private Long latencyMsTotal;
        private String errorCode;
    }

    /**
     * 페이지 응답
     */
    @Getter
    @AllArgsConstructor
    public static class PageResponse<T> {
        private List<T> content;
        private Long totalElements;
        private Integer totalPages;
        private Integer page;
        private Integer size;
    }

    /**
     * 관리자 대시보드 로그 목록 조회 요청
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class LogListRequest {
        @Schema(description = "기간 (7 | 30 | 90)", example = "30")
        private String period;

        @Schema(description = "시작 날짜 (ISO 8601)", example = "2025-12-06T15:00:00.000Z")
        private String startDate;

        @Schema(description = "종료 날짜 (ISO 8601)", example = "2026-01-06T14:59:59.999Z")
        private String endDate;

        @Schema(description = "부서명", example = "총무팀")
        private String department;

        @Schema(description = "도메인 ID", example = "SECURITY")
        private String domain;

        @Schema(description = "라우트 ID", example = "RAG")
        private String route;

        @Schema(description = "모델 ID", example = "gpt-4o-mini")
        private String model;

        @Schema(description = "에러만 보기", example = "false")
        private Boolean onlyError;

        @Schema(description = "PII 포함만 보기", example = "false")
        private Boolean hasPiiOnly;

        @Schema(description = "페이지 번호 (기본값: 0)", example = "0")
        private Integer page;

        @Schema(description = "페이지 크기 (기본값: 20)", example = "20")
        private Integer size;

        @Schema(description = "정렬 (예: createdAt,desc)", example = "createdAt,desc")
        private String sort;

        /**
         * 기본값 적용 및 검증
         */
        public void applyDefaults() {
            if (this.page == null || this.page < 0) {
                this.page = 0;
            }
            if (this.size == null || this.size <= 0) {
                this.size = 20;
            }
        }

        /**
         * period 검증
         */
        public boolean isValidPeriod() {
            if (period == null) {
                return true; // null은 허용
            }
            return period.equals("7") || period.equals("30") || period.equals("90");
        }
    }
}

