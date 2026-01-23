package com.ctrlf.infra.config.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 커스텀 메트릭 등록
 * 
 * <p>백엔드 서버의 비즈니스 지표를 Prometheus로 노출합니다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomMetrics {

    private final MeterRegistry meterRegistry;

    // 비즈니스 메트릭 (고정 태그만 사용)
    private Counter telemetryEventsCollected;

    @PostConstruct
    public void init() {
        // 비즈니스 메트릭 (고정 태그만 사용)
        telemetryEventsCollected = Counter.builder("infra.telemetry.events.collected")
            .description("Total telemetry events collected")
            .tag("application", "infra-service")
            .register(meterRegistry);

        log.info("Custom metrics initialized");
    }

    // API 메트릭
    public void incrementHttpRequest(String method, String path, int statusCode) {
        Counter.builder("infra.http.requests.total")
            .description("Total HTTP requests")
            .tag("application", "infra-service")
            .tag("method", method)
            .tag("path", path)
            .tag("status", String.valueOf(statusCode))
            .register(meterRegistry)
            .increment();
    }

    public void incrementHttpError(String method, String path, int statusCode) {
        Counter.builder("infra.http.errors.total")
            .description("Total HTTP errors")
            .tag("application", "infra-service")
            .tag("method", method)
            .tag("path", path)
            .tag("status", String.valueOf(statusCode))
            .register(meterRegistry)
            .increment();
    }

    public Timer.Sample startHttpRequestTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordHttpRequestDuration(Timer.Sample sample, String method, String path) {
        Timer timer = Timer.builder("infra.http.request.duration")
            .description("HTTP request duration")
            .tag("application", "infra-service")
            .tag("method", method)
            .tag("path", path)
            .register(meterRegistry);
        sample.stop(timer);
    }

    // 비즈니스 메트릭
    public void incrementTelemetryEventsCollected(int count) {
        telemetryEventsCollected.increment(count);
    }

    public void incrementRagDocumentsProcessed(String operation) {
        Counter.builder("infra.rag.documents.processed")
            .description("Total RAG documents processed")
            .tag("application", "infra-service")
            .tag("operation", operation)
            .register(meterRegistry)
            .increment();
    }

    public void incrementS3PresignedUrlsGenerated(String type) {
        Counter.builder("infra.s3.presigned.urls.generated")
            .description("Total S3 presigned URLs generated")
            .tag("application", "infra-service")
            .tag("type", type)
            .register(meterRegistry)
            .increment();
    }

    public void incrementKeycloakUserOperations(String operation) {
        Counter.builder("infra.keycloak.user.operations")
            .description("Total Keycloak user operations")
            .tag("application", "infra-service")
            .tag("operation", operation)
            .register(meterRegistry)
            .increment();
    }
}
