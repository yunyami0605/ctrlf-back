package com.ctrlf.chat.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 설정
 * 
 * <p>API 엔드포인트가 정적 리소스보다 우선순위를 갖도록 설정합니다.</p>
 * <p>Spring Boot 기본 동작은 컨트롤러 매핑이 정적 리소스보다 우선이지만,</p>
 * <p>명시적으로 설정하여 라우팅 충돌을 방지합니다.</p>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * 정적 리소스 핸들러 설정
     * 
     * <p>API 경로(/admin/faq/**)는 정적 리소스 핸들러에서 제외하여</p>
     * <p>컨트롤러 매핑이 우선 처리되도록 합니다.</p>
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 정적 리소스 핸들러는 낮은 우선순위로 설정
        // API 경로는 컨트롤러가 처리하도록 함
        registry.setOrder(Ordered.LOWEST_PRECEDENCE);
    }
}

