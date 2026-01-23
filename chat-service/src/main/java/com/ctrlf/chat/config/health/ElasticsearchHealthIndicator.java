package com.ctrlf.chat.config.health;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Elasticsearch 연결 상태 체크
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchHealthIndicator implements HealthIndicator {

    private final ElasticsearchClient elasticsearchClient;

    @Override
    public Health health() {
        try {
            // Ping 요청으로 Elasticsearch 연결 확인
            boolean pingResult = elasticsearchClient.ping().value();
            if (pingResult) {
                return Health.up()
                    .withDetail("elasticsearch", "Connected")
                    .withDetail("status", "Available")
                    .build();
            } else {
                return Health.down()
                    .withDetail("elasticsearch", "Disconnected")
                    .withDetail("status", "Ping failed")
                    .build();
            }
        } catch (Exception e) {
            log.error("Elasticsearch health check failed", e);
            return Health.down()
                .withDetail("elasticsearch", "Disconnected")
                .withDetail("status", "Connection failed")
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
