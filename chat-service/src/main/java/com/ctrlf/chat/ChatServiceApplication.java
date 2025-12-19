package com.ctrlf.chat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Chat Service 애플리케이션 메인 클래스
 * 
 * <p>채팅 서비스의 Spring Boot 애플리케이션 진입점입니다.</p>
 * <p>채팅 세션 관리, 메시지 처리, FAQ 관리 등의 기능을 제공합니다.</p>
 * 
 * @author CtrlF Team
 * @since 1.0.0
 */
@SpringBootApplication
public class ChatServiceApplication {
    
    /**
     * 애플리케이션 실행 메인 메서드
     * 
     * @param args 명령행 인자
     */
    public static void main(String[] args) {
        SpringApplication.run(ChatServiceApplication.class, args);
    }
}