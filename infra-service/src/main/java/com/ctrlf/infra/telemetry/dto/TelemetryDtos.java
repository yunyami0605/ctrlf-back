package com.ctrlf.infra.telemetry.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 텔레메트리 관련 DTO
 */
public final class TelemetryDtos {
    private TelemetryDtos() {}

    /**
     * 텔레메트리 이벤트 수집 요청
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TelemetryEventRequest {
        @NotBlank
        private String source;

        @NotNull
        private Instant sentAt;

        @NotEmpty
        private List<EventItem> events;
    }

    /**
     * 이벤트 항목
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventItem {
        @NotNull
        private UUID eventId;

        @NotBlank
        private String eventType; // CHAT_TURN | FEEDBACK | SECURITY

        @NotBlank
        private String traceId;

        private String conversationId;
        private Integer turnId;

        @NotBlank
        private String userId;

        @NotBlank
        private String deptId;

        @NotNull
        private Instant occurredAt;

        @NotNull
        private Map<String, Object> payload;
    }

    /**
     * 텔레메트리 이벤트 수집 응답
     */
    @Getter
    @AllArgsConstructor
    public static class TelemetryEventResponse {
        private Integer received;
        private Integer accepted;
        private Integer rejected;
        private List<ErrorItem> errors;
    }

    /**
     * 에러 항목
     */
    @Getter
    @AllArgsConstructor
    public static class ErrorItem {
        private UUID eventId;
        private String errorCode;
        private String message;
    }

    /**
     * 대시보드 지표 - 보안 응답
     */
    @Getter
    @AllArgsConstructor
    public static class SecurityMetricsResponse {
        private Integer piiBlockCount;
        private Integer externalDomainBlockCount;
        private List<PiiTrendItem> piiTrend;
    }

    /**
     * PII 추이 항목
     */
    @Getter
    @AllArgsConstructor
    public static class PiiTrendItem {
        private String bucketStart;
        private Double inputDetectRate;
        private Double outputDetectRate;
    }

    /**
     * 대시보드 지표 - 성능 응답
     */
    @Getter
    @AllArgsConstructor
    public static class PerformanceMetricsResponse {
        private Double dislikeRate;
        private Double repeatRate;
        private String repeatDefinition;
        private Integer oosCount;
        private List<LatencyHistogramItem> latencyHistogram;
        private List<ModelLatencyItem> modelLatency;
    }

    /**
     * 지연시간 히스토그램 항목
     */
    @Getter
    @AllArgsConstructor
    public static class LatencyHistogramItem {
        private String range;
        private Long count;
    }

    /**
     * 모델별 지연시간 항목
     */
    @Getter
    @AllArgsConstructor
    public static class ModelLatencyItem {
        private String model;
        private Double avgLatencyMs;
    }
}

