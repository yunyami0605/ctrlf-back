package com.ctrlf.chat.faq.service;

import com.ctrlf.chat.faq.dto.response.FaqDraftResponse;
import java.util.List;
import java.util.UUID;

/**
 * FAQ Draft(초안) 관리 서비스
 */
public interface FaqDraftService {

    /**
     * FAQ Draft 목록 조회
     */
    List<FaqDraftResponse> getDrafts(String domain, String status);

    /**
     * FAQ Draft 승인 처리
     */
    void approve(UUID draftId, UUID reviewerId, String question, String answer);

    /**
     * FAQ Draft 반려 처리
     */
    void reject(UUID draftId, UUID reviewerId, String reason);
}
