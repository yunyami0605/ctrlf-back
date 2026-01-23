package com.ctrlf.education.config.metrics;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 메트릭 인터셉터 설정
 */
@Configuration
@RequiredArgsConstructor
public class MetricsConfig implements WebMvcConfigurer {

    private final MetricsInterceptor metricsInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(metricsInterceptor)
            .addPathPatterns("/**")
            .excludePathPatterns(
                "/actuator/**",  // Actuator 엔드포인트는 제외 (무한 루프 방지)
                "/error"         // 에러 페이지 제외
            );
    }
}
