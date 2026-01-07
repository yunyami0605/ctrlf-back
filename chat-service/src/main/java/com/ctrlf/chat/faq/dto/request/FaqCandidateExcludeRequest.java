package com.ctrlf.chat.faq.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * FAQ 후보 제외(반려) 요청 DTO
 */
public record FaqCandidateExcludeRequest(
    @NotBlank(message = "반려 사유는 필수입니다.")
    String reason
) {}
