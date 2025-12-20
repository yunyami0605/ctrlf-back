package com.ctrlf.chat.faq.controller;

import com.ctrlf.chat.faq.dto.request.FaqCandidateCreateRequest;
import com.ctrlf.chat.faq.dto.response.FaqCandidateResponse;
import com.ctrlf.chat.faq.dto.response.FaqDraftCreateResponse;
import com.ctrlf.chat.faq.service.FaqCandidateService;
import com.ctrlf.chat.faq.service.FaqService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자용 FAQ 후보 관리 API
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/faq/candidates")
public class AdminFaqCandidateController {

    private final FaqCandidateService faqCandidateService;
    private final FaqService faqService;

    /**
     * FAQ 후보 생성
     */
    @PostMapping
    public ResponseEntity<UUID> createCandidate(@RequestBody FaqCandidateCreateRequest request) {
        UUID candidateId = faqCandidateService.create(request);
        return ResponseEntity.ok(candidateId);
    }

    /**
     * FAQ 후보 목록 조회
     *
     * @param domain 도메인 (선택)
     * @param status 상태 (NEW / ELIGIBLE / EXCLUDED)
     */
    @GetMapping
    public List<FaqCandidateResponse> getCandidates(
        @RequestParam(required = false) String domain,
        @RequestParam(required = false) String status
    ) {
        return faqCandidateService.getCandidates(domain, status);
    }

    /**
     * FAQ 후보 → AI 초안 생성
     */
    @PostMapping("/{candidateId}/generate")
    public FaqDraftCreateResponse generateDraft(
        @PathVariable UUID candidateId
    ) {
        UUID draftId = faqService.generateDraftFromCandidate(candidateId);
        return new FaqDraftCreateResponse(draftId);
    }
}
