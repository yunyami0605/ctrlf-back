package com.ctrlf.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 엔티티를 찾을 수 없을 때 발생하는 예외.
 * 
 * <p>기본적으로 404 NOT_FOUND 상태 코드를 사용합니다.</p>
 */
public class EntityNotFoundException extends BusinessException {

    /**
     * 생성자.
     * 
     * @param message 에러 메시지 (예: "교육을 찾을 수 없습니다: {id}")
     */
    public EntityNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }

    /**
     * 생성자 (엔티티 타입과 ID 포함).
     * 
     * @param entityType 엔티티 타입 (예: "Education", "Video")
     * @param id 엔티티 ID
     */
    public EntityNotFoundException(String entityType, Object id) {
        super(HttpStatus.NOT_FOUND, String.format("%s을(를) 찾을 수 없습니다: %s", entityType, id));
    }
}
