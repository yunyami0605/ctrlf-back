package com.ctrlf.gateway.config.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * API Gateway 연결 상태 체크
 * 
 * <p>Gateway는 데이터베이스를 직접 사용하지 않으므로,
 * 주요 백엔드 서비스들의 연결 상태를 확인합니다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseHealthIndicator implements HealthIndicator {

    private final WebClient.Builder webClientBuilder;

    @Override
    public Health health() {
        try {
            // 주요 백엔드 서비스들의 헬스체크 확인
            WebClient webClient = webClientBuilder.build();
            
            // infra-service 헬스체크
            Boolean infraHealthy = webClient.get()
                .uri("http://localhost:9003/actuator/health")
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> response.contains("\"status\":\"UP\""))
                .timeout(Duration.ofSeconds(2))
                .onErrorReturn(false)
                .block();

            if (infraHealthy != null && infraHealthy) {
                return Health.up()
                    .withDetail("gateway", "API Gateway")
                    .withDetail("status", "Connected")
                    .withDetail("backend-services", "Available")
                    .build();
            } else {
                return Health.down()
                    .withDetail("gateway", "API Gateway")
                    .withDetail("status", "Backend services unavailable")
                    .build();
            }
        } catch (Exception e) {
            log.error("Gateway health check failed", e);
            return Health.down()
                .withDetail("gateway", "API Gateway")
                .withDetail("status", "Health check failed")
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
