package com.ctrlf.education.exception;

import com.ctrlf.common.exception.EntityNotFoundException;
import java.util.UUID;

/**
 * 영상을 찾을 수 없을 때 발생하는 예외.
 */
public class VideoNotFoundException extends EntityNotFoundException {

    /**
     * 생성자.
     */
    public VideoNotFoundException(UUID videoId) {
        super("Video", videoId);
    }

    /**
     * 생성자 (커스텀 메시지).
     * 
     * @param message 에러 메시지
     */
    public VideoNotFoundException(String message) {
        super(message);
    }
}
