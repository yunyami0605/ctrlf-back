package com.ctrlf.chat.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 메시지 피드백 요청 DTO.
 */
public record ChatFeedbackRequest(
    /** 평점 (1: 별로예요, 5: 좋아요) */
    @NotNull(message = "score는 필수입니다")
    Integer score,

    /** 선택 코멘트 (최대 500자) */
    @Size(max = 500, message = "comment는 최대 500자입니다")
    String comment
) {}
