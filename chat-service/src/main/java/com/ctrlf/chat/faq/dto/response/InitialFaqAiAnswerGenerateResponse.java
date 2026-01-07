package com.ctrlf.chat.faq.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 초기 FAQ AI 답변 생성 응답 DTO
 */
@Getter
@AllArgsConstructor
public class InitialFaqAiAnswerGenerateResponse {

    /** 처리 상태 (SUCCESS, PARTIAL, FAILED) */
    private String status;

    /** 전체 초기 FAQ 개수 */
    private Integer totalCount;

    /** 성공적으로 업데이트된 FAQ 개수 */
    private Integer successCount;

    /** 실패한 FAQ 개수 */
    private Integer failCount;

    /** 에러 메시지 (실패 시) */
    private String errorMessage;
}

