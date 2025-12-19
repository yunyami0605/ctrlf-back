package com.ctrlf.chat.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * AI Gateway 전용 WebClient 설정 클래스
 * 
 * <p>AI 서버와의 통신을 위한 WebClient를 설정합니다.</p>
 * <p>application.yml의 ai.gateway.url 값을 사용하여 기본 URL을 설정합니다.</p>
 * 
 * @author CtrlF Team
 * @since 1.0.0
 */
@Configuration
public class AiWebClientConfig {

    /** AI Gateway 서버 URL (application.yml에서 주입) */
    @Value("${ai.gateway.url}")
    private String aiGatewayUrl;

    /**
     * AI Gateway용 WebClient Bean 생성
     * 
     * @return 설정된 WebClient 인스턴스
     */
    @Bean
    public WebClient aiWebClient() {
        return WebClient.builder()
            .baseUrl(aiGatewayUrl)
            .build();
    }
}
