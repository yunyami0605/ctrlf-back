package com.ctrlf.chat.config.metrics;

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
 * <p>채팅 서비스의 비즈니스 지표를 Prometheus로 노출합니다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomMetrics {

    private final MeterRegistry meterRegistry;

    // 비즈니스 메트릭 (고정 태그만 사용)
    private Counter chatMessagesSent;
    private Counter chatSessionsCreated;
    private Counter faqQueries;

    @PostConstruct
    public void init() {
        // 비즈니스 메트릭 (고정 태그만 사용)
        chatMessagesSent = Counter.builder("chat.messages.sent.total")
            .description("Total chat messages sent")
            .tag("application", "chat-service")
            .register(meterRegistry);

        chatSessionsCreated = Counter.builder("chat.sessions.created.total")
            .description("Total chat sessions created")
            .tag("application", "chat-service")
            .register(meterRegistry);

        faqQueries = Counter.builder("chat.faq.queries.total")
            .description("Total FAQ queries")
            .tag("application", "chat-service")
            .register(meterRegistry);

        log.info("Custom metrics initialized");
    }

    // API 메트릭
    public void incrementHttpRequest(String method, String path, int statusCode) {
        Counter.builder("chat.http.requests.total")
            .description("Total HTTP requests")
            .tag("application", "chat-service")
            .tag("method", method)
            .tag("path", path)
            .tag("status", String.valueOf(statusCode))
            .register(meterRegistry)
            .increment();
    }

    public void incrementHttpError(String method, String path, int statusCode) {
        Counter.builder("chat.http.errors.total")
            .description("Total HTTP errors")
            .tag("application", "chat-service")
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
        Timer timer = Timer.builder("chat.http.request.duration")
            .description("HTTP request duration")
            .tag("application", "chat-service")
            .tag("method", method)
            .tag("path", path)
            .register(meterRegistry);
        sample.stop(timer);
    }

    // 비즈니스 메트릭
    public void incrementChatMessagesSent() {
        chatMessagesSent.increment();
    }

    public void incrementChatSessionsCreated() {
        chatSessionsCreated.increment();
    }

    public void incrementFaqQueries() {
        faqQueries.increment();
    }
}
