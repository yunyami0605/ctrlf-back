package com.ctrlf.chat.faq.controller;

import com.ctrlf.chat.faq.service.FaqService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * FAQ 후보 / Draft 관리용 관리자 API
 */
@RestController
@RequestMapping("/admin/faqs")
@RequiredArgsConstructor
public class AdminFaqController {

    private final FaqService faqService;

    /**
     * FAQ 후보 기반 AI Draft 생성
     */
    @PostMapping("/candidates/{id}/generate")
    public UUID generate(@PathVariable UUID id) {
        return faqService.generateDraftFromCandidate(id);
    }

    /**
     * FAQ Draft 승인
     */
    @PostMapping("/drafts/{id}/approve")
    public void approve(
        @PathVariable UUID id,
        @RequestParam UUID reviewerId,
        @RequestParam String question,
        @RequestParam String answer
    ) {
        faqService.approveDraft(id, reviewerId, question, answer);
    }

    /**
     * FAQ Draft 반려
     */
    @PostMapping("/drafts/{id}/reject")
    public void reject(
        @PathVariable UUID id,
        @RequestParam UUID reviewerId,
        @RequestParam String reason
    ) {
        faqService.rejectDraft(id, reviewerId, reason);
    }
}
