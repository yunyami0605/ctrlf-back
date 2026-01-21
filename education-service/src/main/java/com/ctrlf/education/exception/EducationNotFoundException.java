package com.ctrlf.education.exception;

import com.ctrlf.common.exception.EntityNotFoundException;
import java.util.UUID;

/**
 * 교육을 찾을 수 없을 때 발생하는 예외.
 */
public class EducationNotFoundException extends EntityNotFoundException {

    /**
     * 생성자.
     */
    public EducationNotFoundException(UUID educationId) {
        super("Education", educationId);
    }

    /**
     * 생성자 (커스텀 메시지).
     * 
     * @param message 에러 메시지
     */
    public EducationNotFoundException(String message) {
        super(message);
    }
}
