package com.ctrlf.chat.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 설정 클래스
 * 
 * <p>JWT 기반 인증 및 엔드포인트별 접근 권한을 설정합니다.</p>
 * 
 * <ul>
 *   <li>/actuator/** : 인증 불필요 (헬스체크 등)</li>
 *   <li>/api/** : 인증 필요 (JWT 토큰 필수)</li>
 *   <li>기타 경로 : 인증 불필요</li>
 * </ul>
 * 
 * @author CtrlF Team
 * @since 1.0.0
 */
@Configuration
public class SecurityConfig {

    /**
     * Security Filter Chain 설정
     * 
     * @param http HttpSecurity 객체
     * @return 설정된 SecurityFilterChain
     * @throws Exception 설정 중 발생할 수 있는 예외
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // CSRF 보호 비활성화 (REST API이므로 불필요)
            .csrf(csrf -> csrf.disable())
            // 엔드포인트별 접근 권한 설정
            .authorizeHttpRequests(auth -> auth
                // Actuator 엔드포인트는 인증 불필요
                .requestMatchers("/actuator/**").permitAll()
                // /api/** 경로는 인증 필요
                .requestMatchers("/api/**").authenticated()
                // 나머지 경로는 인증 불필요
                .anyRequest().permitAll()
            )
            // OAuth2 Resource Server 설정 (JWT 토큰 검증)
            .oauth2ResourceServer(oauth2 ->
                oauth2.jwt(Customizer.withDefaults())
            );

        return http.build();
    }
}
