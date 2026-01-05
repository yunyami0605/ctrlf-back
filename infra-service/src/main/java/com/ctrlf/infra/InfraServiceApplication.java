package com.ctrlf.infra;

import com.ctrlf.common.security.SecurityConfig;
import com.ctrlf.infra.keycloak.KeycloakAdminProperties;
import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

import java.io.File;
import java.nio.file.Paths;

@SpringBootApplication
@EnableConfigurationProperties({KeycloakAdminProperties.class})
@Import(SecurityConfig.class)
public class InfraServiceApplication {

    private static final Logger log = LoggerFactory.getLogger(InfraServiceApplication.class);

    public static void main(String[] args) {
        // .env 파일 수동 로드 (spring-dotenv가 작동하지 않는 경우 대비)
        loadDotEnv();

        SpringApplication.run(InfraServiceApplication.class, args);
    }

    /**
     * .env 파일을 수동으로 로드하여 시스템 환경변수로 설정합니다.
     * 프로젝트 루트(ctrlf-back/)의 .env 파일을 찾아서 로드합니다.
     */
    private static void loadDotEnv() {
        try {
            // 프로젝트 루트 디렉토리 찾기 (현재 작업 디렉토리 기준)
            String currentDir = System.getProperty("user.dir");
            File envFile = Paths.get(currentDir, ".env").toFile();

            // 상위 디렉토리도 확인 (서브모듈에서 실행하는 경우)
            if (!envFile.exists()) {
                File parentDir = Paths.get(currentDir).getParent().toFile();
                if (parentDir != null) {
                    envFile = new File(parentDir, ".env");
                }
            }

            if (envFile.exists() && envFile.isFile()) {
                log.info(".env 파일 발견: {}", envFile.getAbsolutePath());
                Dotenv dotenv = Dotenv.configure()
                        .directory(envFile.getParent())
                        .filename(".env")
                        .ignoreIfMissing()
                        .load();

                // 환경변수로 설정 (이미 설정된 경우 덮어쓰지 않음)
                dotenv.entries().forEach(entry -> {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    if (System.getenv(key) == null && System.getProperty(key) == null) {
                        System.setProperty(key, value);
                        log.debug(".env에서 환경변수 설정: {}={}", key,
                                key.contains("TOKEN") || key.contains("SECRET") || key.contains("PASSWORD")
                                        ? maskValue(value)
                                        : value);
                    } else {
                        log.debug(".env 변수 {}는 이미 설정되어 있어 건너뜀", key);
                    }
                });

                // AI_INTERNAL_TOKEN 확인
                String aiToken = System.getProperty("AI_INTERNAL_TOKEN");
                if (aiToken == null) {
                    aiToken = System.getenv("AI_INTERNAL_TOKEN");
                }
                if (aiToken != null) {
                    String masked = maskValue(aiToken);
                    log.info("AI_INTERNAL_TOKEN 로드됨: {} (길이: {})", masked, aiToken.length());
                } else {
                    log.warn("AI_INTERNAL_TOKEN이 .env 파일에 없거나 로드되지 않았습니다.");
                }
            } else {
                log.warn(".env 파일을 찾을 수 없습니다. 경로: {}", envFile.getAbsolutePath());
            }
        } catch (Exception e) {
            log.warn(".env 파일 로드 실패 (무시하고 계속 진행): {}", e.getMessage());
        }
    }

    private static String maskValue(String value) {
        if (value == null || value.length() <= 8) {
            return "****";
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }
}