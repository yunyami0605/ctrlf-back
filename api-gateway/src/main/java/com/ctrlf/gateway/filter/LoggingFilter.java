package com.ctrlf.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

/**
 * API Gateway 요청/응답 로깅 필터
 */
@Component
public class LoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(LoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // 1. API URL
        String path = request.getURI().getPath();
        String query = request.getURI().getQuery();
        String apiUrl = query != null ? path + "?" + query : path;

        // 2. Method
        String method = request.getMethod() != null ? request.getMethod().toString() : "UNKNOWN";

        // 3. Headers
        String headers = request.getHeaders().entrySet().stream()
                .map(entry -> entry.getKey() + ": " + String.join(", ", entry.getValue()))
                .collect(Collectors.joining(", "));

        // 4. Bearer Token
        String bearerToken = request.getHeaders().getFirst("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            bearerToken = bearerToken.substring(7); // "Bearer " 제거
        } else {
            bearerToken = "N/A";
        }

        // 5. 라우팅 정보 (대상 서비스)
        Route route = exchange
                .getAttribute(org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        String targetService = "N/A";
        if (route != null && route.getUri() != null) {
            targetService = route.getUri().toString();
        }

        // 요청 로그 출력
        log.info("=== API Gateway Request ===");
        log.info("1. API URL: {}", apiUrl);
        log.info("2. Method: {}", method);
        log.info("3. Headers: {}", headers);
        log.info("4. Bearer Token: {}", bearerToken);
        log.info("5. Target Service: {}", targetService);
        log.info("===========================");

        // 응답 로깅은 일시적으로 비활성화 (익명 클래스 컴파일 문제 해결 전까지)
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        // 낮은 숫자가 먼저 실행됨 (가장 먼저 로깅)
        return -1;
    }
}
