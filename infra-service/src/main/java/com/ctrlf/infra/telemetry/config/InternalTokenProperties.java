package com.ctrlf.infra.telemetry.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 내부 API 토큰 설정
 *
 * <p>AI 서버 → 백엔드 내부 API 인증에 사용되는 토큰 설정입니다.</p>
 *
 * <pre>
 * app:
 *   internal:
 *     token: ${AI_INTERNAL_TOKEN:dev-internal-token}
 * </pre>
 */
@Slf4j
@Component
@ConfigurationProperties(prefix = "app.internal")
public class InternalTokenProperties {

    /**
     * 내부 API 인증 토큰
     *
     * <p>AI 서버의 BACKEND_INTERNAL_TOKEN과 동일한 값으로 설정해야 합니다.</p>
     */
    private String token = "dev-internal-token";

    @PostConstruct
    public void init() {
        // 환경 변수 직접 확인 (디버깅용)
        String envToken = System.getenv("AI_INTERNAL_TOKEN");
        String sysPropToken = System.getProperty("AI_INTERNAL_TOKEN");
        
        log.info("Environment check - AI_INTERNAL_TOKEN from env: {}, from system property: {}", 
            envToken != null ? maskToken(envToken) : "null",
            sysPropToken != null ? maskToken(sysPropToken) : "null");
        
        // 토큰 값 로깅 (보안을 위해 일부만 표시)
        String maskedToken = token != null && token.length() > 8 
            ? token.substring(0, 4) + "***" + token.substring(token.length() - 4)
            : "***";
        log.info("Internal token configured from application.yml: {} (length: {})", 
            maskedToken, token != null ? token.length() : 0);
    }
    
    /**
     * 토큰 마스킹 (디버깅용)
     */
    private String maskToken(String token) {
        if (token == null || token.length() <= 8) {
            return "***";
        }
        return token.substring(0, 4) + "***" + token.substring(token.length() - 4);
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
