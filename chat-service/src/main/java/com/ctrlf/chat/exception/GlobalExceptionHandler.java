package com.ctrlf.chat.exception;

import com.ctrlf.chat.exception.chat.ChatSessionNotFoundException;
import com.ctrlf.chat.faq.exception.FaqNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 전역 예외 처리 핸들러
 * 
 * <p>애플리케이션 전역에서 발생하는 예외를 처리하고 일관된 형식의 에러 응답을 반환합니다.</p>
 * 
 * @author CtrlF Team
 * @since 1.0.0
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 채팅 세션을 찾을 수 없을 때 처리
     * 
     * @param ex ChatSessionNotFoundException 예외
     * @return 404 NOT_FOUND 응답
     */
    @ExceptionHandler(ChatSessionNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleChatSessionNotFound(ChatSessionNotFoundException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", 404);
        response.put("error", "CHAT_SESSION_NOT_FOUND");
        response.put("message", ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * FAQ를 찾을 수 없을 때 처리
     * 
     * @param ex FaqNotFoundException 예외
     * @return 404 NOT_FOUND 응답
     */
    @ExceptionHandler(FaqNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleFaqNotFound(FaqNotFoundException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", 404);
        response.put("error", "FAQ_NOT_FOUND");
        response.put("message", ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * 그 외 모든 예외 처리
     * 
     * <p>처리되지 않은 모든 예외를 500 Internal Server Error로 처리합니다.</p>
     * 
     * @param ex 발생한 예외
     * @return 500 INTERNAL_SERVER_ERROR 응답
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", 500);
        response.put("error", "INTERNAL_SERVER_ERROR");
        response.put("message", ex.getMessage());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
