package com.ctrlf.gateway.filter;

import com.ctrlf.gateway.config.metrics.CustomMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * API Gateway 메트릭 수집 필터
 * 
 * <p>Spring Cloud Gateway는 WebFlux 기반이므로 GlobalFilter를 사용합니다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricsFilter implements GlobalFilter, Ordered {

    private final CustomMetrics customMetrics;
    private static final String TIMER_SAMPLE_ATTRIBUTE = "metrics.timer.sample";
    private static final String START_TIME_ATTRIBUTE = "metrics.start.time";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String method = request.getMethod() != null ? request.getMethod().toString() : "UNKNOWN";
        String path = request.getURI().getPath();
        
        // 라우팅 정보 가져오기
        Route route = exchange.getAttribute(
            org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR
        );
        String routeId = route != null ? route.getId() : "unknown";

        // 타이머 시작
        io.micrometer.core.instrument.Timer.Sample sample = customMetrics.startHttpRequestTimer();
        exchange.getAttributes().put(TIMER_SAMPLE_ATTRIBUTE, sample);
        exchange.getAttributes().put(START_TIME_ATTRIBUTE, System.currentTimeMillis());

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            ServerHttpResponse response = exchange.getResponse();
            int statusCode = response.getStatusCode() != null ? response.getStatusCode().value() : 0;

            // HTTP 요청 카운터 증가
            customMetrics.incrementHttpRequest(method, path, routeId, statusCode);

            // 에러 카운터 증가 (4xx, 5xx)
            if (statusCode >= 400) {
                customMetrics.incrementHttpError(method, path, routeId, statusCode);
            }

            // 타이머 종료
            io.micrometer.core.instrument.Timer.Sample timerSample = 
                exchange.getAttribute(TIMER_SAMPLE_ATTRIBUTE);
            if (timerSample != null) {
                customMetrics.recordHttpRequestDuration(timerSample, method, path, routeId);
            }
        }));
    }

    @Override
    public int getOrder() {
        // LoggingFilter(-1) 다음에 실행되도록 설정
        return 0;
    }
}
