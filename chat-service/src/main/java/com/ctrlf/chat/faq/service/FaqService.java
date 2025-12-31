package com.ctrlf.chat.faq.service;

import com.ctrlf.chat.faq.dto.request.AutoFaqGenerateRequest;
import com.ctrlf.chat.faq.dto.request.FaqDraftGenerateBatchRequest;
import com.ctrlf.chat.faq.dto.request.FaqUpdateRequest;
import com.ctrlf.chat.faq.dto.response.AutoFaqGenerateResponse;
import com.ctrlf.chat.faq.dto.response.FaqDraftGenerateBatchResponse;
import com.ctrlf.chat.faq.dto.response.FaqResponse;

import java.util.List;
import java.util.UUID;

/**
 * FAQ 서비스 인터페이스
 * 
 * <p>FAQ는 AI가 초안을 생성하고 관리자가 승인하는 구조입니다.
 * 관리자는 AI가 생성한 FAQ를 수정하거나 삭제(비활성화)할 수 있습니다.</p>
 */
public interface FaqService {

    /** 게시 FAQ 조회 */
    List<FaqResponse> getAll();

    /** FAQ 수정 (AI가 생성한 FAQ를 관리자가 수정) */
    void update(UUID id, FaqUpdateRequest request);

    /** FAQ 삭제(비활성화) (AI가 생성한 FAQ를 관리자가 비활성화) */
    void delete(UUID id);

    // =========================
    // FAQ 자동 생성 연계
    // =========================

    /** 자동 FAQ 생성 (질문 로그 기반) */
    AutoFaqGenerateResponse generateAuto(AutoFaqGenerateRequest request);

    /** FAQ 후보 → AI 초안 생성 */
    UUID generateDraftFromCandidate(UUID candidateId);

    /** FAQ 초안 배치 생성 */
    FaqDraftGenerateBatchResponse generateDraftBatch(FaqDraftGenerateBatchRequest request);

    /** AI 초안 승인 → FAQ 게시 */
    void approveDraft(UUID draftId, UUID reviewerId, String question, String answer);

    /** AI 초안 반려 */
    void rejectDraft(UUID draftId, UUID reviewerId, String reason);
}
