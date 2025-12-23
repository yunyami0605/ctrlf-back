package com.ctrlf.education.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Web MVC 설정.
 * UTF-8 인코딩을 명시적으로 설정하여 Swagger UI에서 한글 요청 시 발생하는 인코딩 문제를 해결합니다.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * HTTP 메시지 컨버터에 UTF-8 인코딩을 명시적으로 설정.
     * Swagger UI에서 한글이 포함된 JSON 요청 시 인코딩 문제를 방지합니다.
     */
    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        // JSON 메시지 컨버터를 찾아서 UTF-8 인코딩 설정
        converters.forEach(converter -> {
            if (converter instanceof MappingJackson2HttpMessageConverter) {
                MappingJackson2HttpMessageConverter jsonConverter = 
                    (MappingJackson2HttpMessageConverter) converter;
                
                // UTF-8을 기본 charset으로 설정
                jsonConverter.setDefaultCharset(StandardCharsets.UTF_8);
                
                // 지원하는 MediaType에 UTF-8 charset 추가
                List<MediaType> supportedMediaTypes = new ArrayList<>();
                supportedMediaTypes.add(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8));
                supportedMediaTypes.add(new MediaType("application", "*+json", StandardCharsets.UTF_8));
                jsonConverter.setSupportedMediaTypes(supportedMediaTypes);
            }
        });
    }

    /**
     * CharacterEncodingFilter를 가장 먼저 실행되도록 등록하여 UTF-8 인코딩을 강제합니다.
     * 
     * 필터 순서를 최우선(Ordered.HIGHEST_PRECEDENCE)으로 설정하여
     * 요청 본문이 읽히기 전에 인코딩이 적용되도록 합니다.
     * 
     * Swagger UI나 curl에서 Content-Type에 charset이 없거나 잘못된 인코딩으로
     * 요청을 보낼 때도 UTF-8로 강제 변환합니다.
     * 
     * 참고: Spring Boot의 자동 설정된 CharacterEncodingFilter와 충돌을 피하기 위해
     * 다른 이름으로 등록하고, 필터 순서를 최우선으로 설정합니다.
     */
    @Bean
    public FilterRegistrationBean<CharacterEncodingFilter> customCharacterEncodingFilter() {
        CharacterEncodingFilter filter = new CharacterEncodingFilter();
        filter.setEncoding("UTF-8");
        filter.setForceEncoding(true);
        filter.setForceRequestEncoding(true);  // 요청 인코딩 강제
        filter.setForceResponseEncoding(true); // 응답 인코딩 강제
        
        FilterRegistrationBean<CharacterEncodingFilter> registration = 
            new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE); // 가장 먼저 실행
        registration.addUrlPatterns("/*"); // 모든 URL에 적용
        registration.setName("customCharacterEncodingFilter"); // 고유한 이름 설정
        return registration;
    }
}
