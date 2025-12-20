package com.ctrlf.chat.faq.service;

import com.ctrlf.chat.faq.dto.request.FaqCandidateCreateRequest;
import com.ctrlf.chat.faq.dto.response.FaqCandidateResponse;
import java.util.List;
import java.util.UUID;

/**
 * FAQ 후보 질문(Candidate) 관리 서비스
 */
public interface FaqCandidateService {

    /**
     * FAQ 후보 생성
     */
    UUID create(FaqCandidateCreateRequest request);

    /**
     * FAQ 후보 목록 조회
     */
    List<FaqCandidateResponse> getCandidates(String domain, String status);

    /**
     * FAQ 후보 단건 조회
     */
    FaqCandidateResponse getCandidate(UUID id);

    /**
     * FAQ 후보 제외 처리
     */
    void excludeCandidate(UUID id, String reason);
}
