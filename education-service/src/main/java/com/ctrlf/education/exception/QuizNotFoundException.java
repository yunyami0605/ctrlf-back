package com.ctrlf.education.exception;

import com.ctrlf.common.exception.EntityNotFoundException;
import java.util.UUID;

/**
 * 퀴즈를 찾을 수 없을 때 발생하는 예외.
 */
public class QuizNotFoundException extends EntityNotFoundException {

    /**
     * 생성자.
     */
    public QuizNotFoundException(UUID quizId) {
        super("Quiz", quizId);
    }

    /**
     * 생성자 (커스텀 메시지).
     * 
     * @param message 에러 메시지
     */
    public QuizNotFoundException(String message) {
        super(message);
    }
}
