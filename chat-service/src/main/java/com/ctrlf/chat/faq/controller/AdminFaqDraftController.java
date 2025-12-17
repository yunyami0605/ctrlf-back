package com.ctrlf.chat.faq.controller;

import com.ctrlf.chat.faq.dto.response.FaqDraftResponse;
import com.ctrlf.chat.faq.service.FaqDraftService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/faq/drafts")
public class AdminFaqDraftController {

    private final FaqDraftService faqDraftService;

    /**
     * 관리자 FAQ 초안 목록 조회
     */
    @GetMapping
    public List<FaqDraftResponse> getDrafts(
        @RequestParam(required = false) String domain,
        @RequestParam(required = false) String status
    ) {
        return faqDraftService.getDrafts(domain, status);
    }

    /**
     * 초안 승인
     */
    @PostMapping("/{draftId}/approve")
    public void approve(
        @PathVariable UUID draftId,
        @RequestParam UUID reviewerId,
        @RequestParam String question,
        @RequestParam String answer
    ) {
        faqDraftService.approve(draftId, reviewerId, question, answer);
    }

    /**
     * 초안 반려
     */
    @PostMapping("/{draftId}/reject")
    public void reject(
        @PathVariable UUID draftId,
        @RequestParam UUID reviewerId,
        @RequestParam String reason
    ) {
        faqDraftService.reject(draftId, reviewerId, reason);
    }
}
