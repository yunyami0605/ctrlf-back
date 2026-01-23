package com.ctrlf.gateway.config.metrics;

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
 * <p>API Gateway의 비즈니스 지표를 Prometheus로 노출합니다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomMetrics {

    private final MeterRegistry meterRegistry;

    @PostConstruct
    public void init() {
        log.info("Custom metrics initialized for API Gateway");
    }

    // API 메트릭
    public void incrementHttpRequest(String method, String path, String routeId, int statusCode) {
        Counter.builder("gateway.http.requests.total")
            .description("Total HTTP requests through gateway")
            .tag("application", "api-gateway")
            .tag("method", method)
            .tag("path", getNormalizedPath(path))
            .tag("route", routeId != null ? routeId : "unknown")
            .tag("status", String.valueOf(statusCode))
            .register(meterRegistry)
            .increment();
    }

    public void incrementHttpError(String method, String path, String routeId, int statusCode) {
        Counter.builder("gateway.http.errors.total")
            .description("Total HTTP errors through gateway")
            .tag("application", "api-gateway")
            .tag("method", method)
            .tag("path", getNormalizedPath(path))
            .tag("route", routeId != null ? routeId : "unknown")
            .tag("status", String.valueOf(statusCode))
            .register(meterRegistry)
            .increment();
    }

    public Timer.Sample startHttpRequestTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordHttpRequestDuration(Timer.Sample sample, String method, String path, String routeId) {
        Timer timer = Timer.builder("gateway.http.request.duration")
            .description("HTTP request duration through gateway")
            .tag("application", "api-gateway")
            .tag("method", method)
            .tag("path", getNormalizedPath(path))
            .tag("route", routeId != null ? routeId : "unknown")
            .register(meterRegistry);
        sample.stop(timer);
    }

    private String getNormalizedPath(String path) {
        if (path == null) {
            return "unknown";
        }
        // Actuator 엔드포인트는 경로 단순화
        if (path.startsWith("/actuator")) {
            return "/actuator/**";
        }
        // 파라미터 제거 (예: /api/users/123 -> /api/users/{id})
        return path.replaceAll("/\\d+", "/{id}")
            .replaceAll("/[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}", "/{uuid}");
    }
}
