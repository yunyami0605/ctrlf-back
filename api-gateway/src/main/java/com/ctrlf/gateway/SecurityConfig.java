package com.ctrlf.gateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchange -> exchange
                // CORS preflight 요청 허용
                .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // Actuator 엔드포인트 허용 (모니터링용)
                .pathMatchers("/actuator/health", "/actuator/info", "/actuator/metrics", "/actuator/prometheus").permitAll()
                // 내부 API는 인증 우회 (내부 서비스 간 통신용)
                // X-Internal-Token 헤더로 인증하는 것은 각 서비스에서 처리
                .pathMatchers("/internal/**").permitAll()
                // 토큰 발급 엔드포인트는 인증 우회
                .pathMatchers("/admin/users/token/**").permitAll()
                // 개인화 API는 인증 우회 (AI Gateway에서 X-User-Id 헤더로 사용자 식별)
                .pathMatchers("/api/personalization/**").permitAll()
                // 나머지는 JWT 인증 필요
                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt());
        return http.build();
    }
}

