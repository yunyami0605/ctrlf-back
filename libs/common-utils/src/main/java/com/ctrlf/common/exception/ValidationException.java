package com.ctrlf.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 검증 실패 예외.
 * 
 * <p>요청 데이터의 검증에 실패했을 때 발생하는 예외입니다.</p>
 * <p>기본적으로 400 BAD_REQUEST 상태 코드를 사용합니다.</p>
 * 
 * @author CtrlF Team
 * @since 1.0.0
 */
public class ValidationException extends BusinessException {

    /**
     * 생성자.
     * 
     * @param message 검증 실패 메시지
     */
    public ValidationException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * 생성자 (필드명 포함).
     * 
     * @param fieldName 검증 실패한 필드명
     * @param message 검증 실패 메시지
     */
    public ValidationException(String fieldName, String message) {
        super(HttpStatus.BAD_REQUEST, String.format("%s: %s", fieldName, message));
    }
}
