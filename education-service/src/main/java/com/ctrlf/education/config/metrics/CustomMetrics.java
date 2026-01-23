package com.ctrlf.education.config.metrics;

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
 * <p>교육 서비스의 비즈니스 지표를 Prometheus로 노출합니다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomMetrics {

    private final MeterRegistry meterRegistry;

    // 비즈니스 메트릭 (고정 태그만 사용)
    private Counter educationViews;
    private Counter videoPlays;
    private Counter videoProgressUpdates;
    private Counter quizAttempts;
    private Counter scriptGenerations;

    @PostConstruct
    public void init() {
        // 비즈니스 메트릭 (고정 태그만 사용)
        educationViews = Counter.builder("education.views.total")
            .description("Total education views")
            .tag("application", "education-service")
            .register(meterRegistry);

        videoPlays = Counter.builder("education.video.plays.total")
            .description("Total video plays")
            .tag("application", "education-service")
            .register(meterRegistry);

        videoProgressUpdates = Counter.builder("education.video.progress.updates.total")
            .description("Total video progress updates")
            .tag("application", "education-service")
            .register(meterRegistry);

        quizAttempts = Counter.builder("education.quiz.attempts.total")
            .description("Total quiz attempts")
            .tag("application", "education-service")
            .register(meterRegistry);

        scriptGenerations = Counter.builder("education.script.generations.total")
            .description("Total script generations")
            .tag("application", "education-service")
            .register(meterRegistry);

        log.info("Custom metrics initialized");
    }

    // API 메트릭
    public void incrementHttpRequest(String method, String path, int statusCode) {
        Counter.builder("education.http.requests.total")
            .description("Total HTTP requests")
            .tag("application", "education-service")
            .tag("method", method)
            .tag("path", path)
            .tag("status", String.valueOf(statusCode))
            .register(meterRegistry)
            .increment();
    }

    public void incrementHttpError(String method, String path, int statusCode) {
        Counter.builder("education.http.errors.total")
            .description("Total HTTP errors")
            .tag("application", "education-service")
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
        Timer timer = Timer.builder("education.http.request.duration")
            .description("HTTP request duration")
            .tag("application", "education-service")
            .tag("method", method)
            .tag("path", path)
            .register(meterRegistry);
        sample.stop(timer);
    }

    // 비즈니스 메트릭
    public void incrementEducationViews() {
        educationViews.increment();
    }

    public void incrementVideoPlays() {
        videoPlays.increment();
    }

    public void incrementVideoProgressUpdates() {
        videoProgressUpdates.increment();
    }

    public void incrementQuizAttempts() {
        quizAttempts.increment();
    }

    public void incrementScriptGenerations() {
        scriptGenerations.increment();
    }
}
