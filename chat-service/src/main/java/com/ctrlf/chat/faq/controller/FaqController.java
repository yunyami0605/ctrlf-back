package com.ctrlf.chat.faq.controller;

import com.ctrlf.chat.faq.dto.request.FaqUpdateRequest;
import com.ctrlf.chat.faq.dto.response.FaqResponse;
import com.ctrlf.chat.faq.service.FaqService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * FAQ 관리 API 컨트롤러
 * 
 * <p>FAQ는 AI가 초안을 생성하고 관리자가 승인하는 구조입니다.
 * 관리자는 AI가 생성한 FAQ를 수정하거나 삭제(비활성화)할 수 있습니다.</p>
 */
@RestController
@RequestMapping("/chat/faq")
@RequiredArgsConstructor
public class FaqController {

    private final FaqService faqService;

    /**
     * FAQ 조회 (유저)
     * 
     * <p>활성화된 FAQ 목록을 조회합니다.</p>
     */
    @GetMapping
    public ResponseEntity<List<FaqResponse>> getAll() {
        return ResponseEntity.ok(faqService.getAll());
    }

    /**
     * FAQ 수정 (관리자)
     * 
     * <p>AI가 생성한 FAQ의 내용을 관리자가 수정합니다.</p>
     * 
     * @param faqId FAQ ID (수정할 FAQ의 UUID)
     * @param request FAQ 수정 요청 (질문, 답변, 도메인, 활성 여부, 우선순위)
     */
    @PatchMapping("/{faqId}")
    public ResponseEntity<Void> update(
        @PathVariable UUID faqId,
        @RequestBody FaqUpdateRequest request
    ) {
        faqService.update(faqId, request);
        return ResponseEntity.ok().build();
    }

    /**
     * FAQ 삭제 (관리자 - Soft Delete)
     * 
     * <p>AI가 생성한 FAQ를 비활성화합니다. 실제로는 isActive = false로 설정하는 소프트 삭제입니다.</p>
     * 
     * @param faqId FAQ ID (삭제할 FAQ의 UUID)
     */
    @DeleteMapping("/{faqId}")
    public ResponseEntity<Void> delete(@PathVariable UUID faqId) {
        faqService.delete(faqId);
        return ResponseEntity.ok().build();
    }
}
