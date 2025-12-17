package com.ctrlf.chat.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * AI Gateway 전용 WebClient 설정
 */
@Configuration
public class AiWebClientConfig {

    @Value("${ai.gateway.url}")
    private String aiGatewayUrl;

    @Bean
    public WebClient aiWebClient() {
        return WebClient.builder()
            .baseUrl(aiGatewayUrl)
            .build();
    }
}
