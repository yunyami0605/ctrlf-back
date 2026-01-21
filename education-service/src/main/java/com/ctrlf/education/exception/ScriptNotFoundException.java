package com.ctrlf.education.exception;

import com.ctrlf.common.exception.EntityNotFoundException;
import java.util.UUID;

/**
 * 스크립트를 찾을 수 없을 때 발생하는 예외.
 */
public class ScriptNotFoundException extends EntityNotFoundException {

    /**
     * 생성자.
     */
    public ScriptNotFoundException(UUID scriptId) {
        super("Script", scriptId);
    }

    /**
     * 생성자 (커스텀 메시지).
     */
    public ScriptNotFoundException(String message) {
        super(message);
    }
}
