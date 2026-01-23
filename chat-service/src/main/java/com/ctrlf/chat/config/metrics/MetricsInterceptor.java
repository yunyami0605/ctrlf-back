package com.ctrlf.chat.config.metrics;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * HTTP 요청 메트릭 수집 인터셉터
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricsInterceptor implements HandlerInterceptor {

    private final CustomMetrics customMetrics;
    private static final String TIMER_SAMPLE_ATTRIBUTE = "metrics.timer.sample";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 타이머 시작
        io.micrometer.core.instrument.Timer.Sample sample = customMetrics.startHttpRequestTimer();
        request.setAttribute(TIMER_SAMPLE_ATTRIBUTE, sample);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        String method = request.getMethod();
        String path = getPath(request);
        int statusCode = response.getStatus();

        // HTTP 요청 카운터 증가
        customMetrics.incrementHttpRequest(method, path, statusCode);

        // 에러 카운터 증가 (4xx, 5xx)
        if (statusCode >= 400) {
            customMetrics.incrementHttpError(method, path, statusCode);
        }

        // 타이머 종료
        io.micrometer.core.instrument.Timer.Sample sample = 
            (io.micrometer.core.instrument.Timer.Sample) request.getAttribute(TIMER_SAMPLE_ATTRIBUTE);
        if (sample != null) {
            customMetrics.recordHttpRequestDuration(sample, method, path);
        }
    }

    private String getPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Actuator 엔드포인트는 경로 단순화
        if (path.startsWith("/actuator")) {
            return "/actuator/**";
        }
        // 파라미터 제거 (예: /api/users/123 -> /api/users/{id})
        return path.replaceAll("/\\d+", "/{id}")
            .replaceAll("/[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}", "/{uuid}");
    }
}
