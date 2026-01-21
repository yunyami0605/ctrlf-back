package com.ctrlf.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 비즈니스 로직 예외 기본 클래스.
 */
@Getter
public class BusinessException extends RuntimeException {

    /** HTTP 상태 코드 */
    private final HttpStatus status;

    /**
     * 생성자.
     * 
     * @param status HTTP 상태 코드
     * @param message 에러 메시지
     */
    public BusinessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    /**
     * 생성자 (400 Bad Request 기본값).
     * 
     * @param message 에러 메시지
     */
    public BusinessException(String message) {
        this(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * 생성자 (원인 예외 포함).
     * 
     * @param status HTTP 상태 코드
     * @param message 에러 메시지
     * @param cause 원인 예외
     */
    public BusinessException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }
}
