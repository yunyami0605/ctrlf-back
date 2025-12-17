package com.ctrlf.chat.faq.service;

import com.ctrlf.chat.faq.dto.request.FaqCreateRequest;
import com.ctrlf.chat.faq.dto.request.FaqUpdateRequest;
import com.ctrlf.chat.faq.dto.response.FaqResponse;

import java.util.List;
import java.util.UUID;

public interface FaqService {

    /** (기존) FAQ 수동 생성 */
    UUID create(FaqCreateRequest request);

    /** (기존) FAQ 수정 */
    void update(UUID id, FaqUpdateRequest request);

    /** (기존) FAQ 삭제(비활성) */
    void delete(UUID id);

    /** (기존) 게시 FAQ 조회 */
    List<FaqResponse> getAll();

    // =========================
    // FAQ 자동 생성 연계 (추가)
    // =========================

    /** FAQ 후보 → AI 초안 생성 */
    UUID generateDraftFromCandidate(UUID candidateId);

    /** AI 초안 승인 → FAQ 게시 */
    void approveDraft(UUID draftId, UUID reviewerId, String question, String answer);

    /** AI 초안 반려 */
    void rejectDraft(UUID draftId, UUID reviewerId, String reason);
}
