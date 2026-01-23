package com.ctrlf.infra.config.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * AWS S3 연결 상태 체크
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class S3HealthIndicator implements HealthIndicator {

    private final S3Client s3Client;

    @Value("${app.s3.bucket:ctrl-s3}")
    private String bucketName;

    @Override
    public Health health() {
        try {
            // HeadBucket으로 S3 버킷 접근 확인
            s3Client.headBucket(HeadBucketRequest.builder()
                .bucket(bucketName)
                .build());
            
            return Health.up()
                .withDetail("s3", "Connected")
                .withDetail("bucket", bucketName)
                .withDetail("status", "Available")
                .build();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                log.warn("S3 bucket not found: {}", bucketName);
                return Health.down()
                    .withDetail("s3", "Bucket not found")
                    .withDetail("bucket", bucketName)
                    .withDetail("error", "Bucket does not exist")
                    .build();
            } else {
                log.error("S3 health check failed", e);
                return Health.down()
                    .withDetail("s3", "Connection failed")
                    .withDetail("bucket", bucketName)
                    .withDetail("error", e.getMessage())
                    .build();
            }
        } catch (Exception e) {
            log.error("S3 health check failed", e);
            return Health.down()
                .withDetail("s3", "Connection failed")
                .withDetail("bucket", bucketName)
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
