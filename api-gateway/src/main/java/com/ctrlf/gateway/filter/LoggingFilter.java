package com.ctrlf.gateway.filter;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
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
        Instant startTime = Instant.now();

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
        Route route = exchange.getAttribute(org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
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

        // 응답 바디를 읽기 위한 데코레이터
        ServerHttpResponse originalResponse = exchange.getResponse();
        DataBufferFactory bufferFactory = originalResponse.bufferFactory();

        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                if (body instanceof Flux) {
                    Flux<? extends DataBuffer> fluxBody = (Flux<? extends DataBuffer>) body;
                    return super.writeWith(fluxBody.buffer().map(dataBuffers -> {
                        DataBufferFactory factory = bufferFactory();
                        DataBuffer joined = factory.join(dataBuffers);
                        byte[] content = new byte[joined.readableByteCount()];
                        joined.read(content);
                        DataBufferUtils.release(joined);

                        // 응답 바디를 문자열로 변환
                        String responseBody = new String(content, StandardCharsets.UTF_8);
                        
                        Instant endTime = Instant.now();
                        Duration duration = Duration.between(startTime, endTime);
                        int statusCode = getStatusCode() != null ? getStatusCode().value() : 0;

                        // 응답 로그 출력
                        log.info("=== API Gateway Response ===");
                        log.info("API URL: {} | Method: {} | Status: {} | Duration: {}ms", 
                            apiUrl, method, statusCode, duration.toMillis());
                        log.info("Response Body: {}", responseBody);
                        log.info("============================");

                        return factory.wrap(content);
                    }));
                }
                return super.writeWith(body);
            }
        };

        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }

    @Override
    public int getOrder() {
        // 낮은 숫자가 먼저 실행됨 (가장 먼저 로깅)
        return -1;
    }
}

