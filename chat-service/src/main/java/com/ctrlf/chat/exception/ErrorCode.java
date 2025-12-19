package com.ctrlf.chat.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 에러 코드 열거형
 * 
 * <p>애플리케이션에서 사용하는 에러 코드를 정의합니다.</p>
 * <p>각 에러 코드는 HTTP 상태 코드, 에러 코드 문자열, 에러 메시지를 포함합니다.</p>
 * 
 * @author CtrlF Team
 * @since 1.0.0
 */
@Getter
public enum ErrorCode {

    /** 공통 에러 코드 */
    /** 서버 내부 오류 */
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C001", "서버 내부 오류"),
    /** 잘못된 요청 */
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "C002", "잘못된 요청입니다."),

    /** 채팅 도메인 에러 코드 */
    /** 채팅 세션을 찾을 수 없음 */
    CHAT_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT_001", "채팅 세션을 찾을 수 없습니다."),
    /** 채팅 섹션을 찾을 수 없음 */
    CHAT_SECTION_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT_002", "채팅 섹션을 찾을 수 없습니다."),
    /** 채팅 메시지를 찾을 수 없음 */
    CHAT_MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT_003", "채팅 메시지를 찾을 수 없습니다.");

    /** HTTP 상태 코드 */
    private final HttpStatus status;
    /** 에러 코드 문자열 */
    private final String code;
    /** 에러 메시지 */
    private final String message;

    /**
     * 생성자
     * 
     * @param status HTTP 상태 코드
     * @param code 에러 코드 문자열
     * @param message 에러 메시지
     */
    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }
}
