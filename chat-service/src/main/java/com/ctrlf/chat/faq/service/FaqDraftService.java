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
     * 
     * @param draftUuid UUID 형식의 draftId (null이면 faqDraftId 사용)
     * @param draftIdString String 형식의 draftId (UUID 또는 faqDraftId)
     * @param reviewerId 승인자 ID
     * @param question 승인할 질문
     * @param answer 승인할 답변
     */
    void approve(UUID draftUuid, String draftIdString, UUID reviewerId, String question, String answer);

    /**
     * FAQ Draft 반려 처리
     * 
     * @param draftUuid UUID 형식의 draftId (null이면 faqDraftId 사용)
     * @param draftIdString String 형식의 draftId (UUID 또는 faqDraftId)
     * @param reviewerId 반려자 ID
     * @param reason 반려 사유
     */
    void reject(UUID draftUuid, String draftIdString, UUID reviewerId, String reason);

    /**
     * FAQ Draft 삭제 처리
     * 
     * <p>승인됨 또는 반려됨 상태의 Draft를 삭제합니다.</p>
     * 
     * @param draftUuid UUID 형식의 draftId (null이면 faqDraftId 사용)
     * @param draftIdString String 형식의 draftId (UUID 또는 faqDraftId)
     * @param reviewerId 삭제자 ID
     */
    void delete(UUID draftUuid, String draftIdString, UUID reviewerId);
}
