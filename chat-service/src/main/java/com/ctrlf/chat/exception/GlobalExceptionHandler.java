package com.ctrlf.chat.exception;

import com.ctrlf.chat.exception.chat.ChatSessionNotFoundException;
import com.ctrlf.chat.faq.exception.FaqNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 전역 예외 처리 핸들러
 * 
 * <p>애플리케이션 전역에서 발생하는 예외를 처리하고 일관된 형식의 에러 응답을 반환합니다.</p>
 * 
 * <p>SSE(Server-Sent Events) 요청의 경우, 이미 text/event-stream 응답이 시작되었으므로
 * JSON 응답을 시도하지 않고 예외를 그대로 전파합니다.</p>
 * 
 * @author CtrlF Team
 * @since 1.0.0
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 현재 요청이 SSE 요청인지 확인합니다.
     * 
     * @return SSE 요청이면 true, 아니면 false
     */
    private boolean isSseRequest() {
        ServletRequestAttributes attributes = 
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return false;
        }
        
        HttpServletRequest request = attributes.getRequest();
        String acceptHeader = request.getHeader("Accept");
        String contentType = request.getContentType();
        
        // Accept 헤더에 text/event-stream이 포함되어 있거나
        // Content-Type이 text/event-stream인 경우 SSE 요청으로 판단
        return (acceptHeader != null && acceptHeader.contains(MediaType.TEXT_EVENT_STREAM_VALUE))
            || MediaType.TEXT_EVENT_STREAM_VALUE.equals(contentType);
    }

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
     * 잘못된 인자 예외 처리
     * 
     * <p>IllegalArgumentException을 400 Bad Request로 처리합니다.</p>
     * 
     * <p>SSE 요청의 경우, 이미 text/event-stream 응답이 시작되었으므로
     * 예외를 그대로 전파하여 서비스 레이어에서 SSE 에러 이벤트로 처리하도록 합니다.</p>
     * 
     * @param ex IllegalArgumentException 예외
     * @return 400 BAD_REQUEST 응답 (SSE 요청이 아닌 경우)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        // SSE 요청인 경우 예외를 그대로 전파 (서비스 레이어에서 SSE 에러 이벤트로 처리)
        if (isSseRequest()) {
            throw ex;
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", 400);
        response.put("error", "BAD_REQUEST");
        response.put("message", ex.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 그 외 모든 예외 처리
     * 
     * <p>처리되지 않은 모든 예외를 500 Internal Server Error로 처리합니다.</p>
     * 
     * <p>SSE 요청의 경우, 이미 text/event-stream 응답이 시작되었으므로
     * 예외를 그대로 전파하여 서비스 레이어에서 SSE 에러 이벤트로 처리하도록 합니다.</p>
     * 
     * @param ex 발생한 예외
     * @return 500 INTERNAL_SERVER_ERROR 응답 (SSE 요청이 아닌 경우)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception ex) throws Exception {
        // SSE 요청인 경우 예외를 그대로 전파 (서비스 레이어에서 SSE 에러 이벤트로 처리)
        if (isSseRequest()) {
            throw ex;
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", 500);
        response.put("error", "INTERNAL_SERVER_ERROR");
        response.put("message", ex.getMessage());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
