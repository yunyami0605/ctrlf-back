package com.ctrlf.chat.faq.controller;

import com.ctrlf.chat.faq.dto.request.AutoFaqGenerateRequest;
import com.ctrlf.chat.faq.dto.request.FaqDraftApproveRequest;
import com.ctrlf.chat.faq.dto.request.FaqDraftRejectRequest;
import com.ctrlf.chat.faq.dto.response.AutoFaqGenerateResponse;
import com.ctrlf.chat.faq.dto.response.FaqDraftResponse;
import com.ctrlf.chat.faq.service.FaqDraftService;
import com.ctrlf.chat.faq.service.FaqService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/faq/drafts")
public class AdminFaqDraftController {

    private final FaqDraftService faqDraftService;
    private final FaqService faqService;

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
     * 초안 승인 (Request Body 방식 - 권장)
     * 
     * <p>프론트엔드에서 사용하기 편리한 Request Body 방식입니다.
     * 기존 Query Parameter 방식도 하위 호환성을 위해 유지됩니다.</p>
     * 
     * <p>draftId는 UUID 또는 faqDraftId(String) 모두 지원합니다.</p>
     */
    @PostMapping("/{draftId}/approve")
    public void approve(
        @PathVariable String draftId,
        @RequestBody(required = false) FaqDraftApproveRequest requestBody,
        @RequestParam(required = false) UUID reviewerId,
        @RequestParam(required = false) String question,
        @RequestParam(required = false) String answer
    ) {
        log.info("[FAQ Draft 승인] 요청 수신: draftId={}, requestBody={}, reviewerId={}, question={}, answer={}", 
            draftId, requestBody != null ? "present" : "null", reviewerId, question, answer);
        
        UUID finalReviewerId;
        String finalQuestion;
        String finalAnswer;
        
        // Request Body가 있으면 우선 사용, 없으면 Query Parameter 사용
        if (requestBody != null) {
            log.debug("[FAQ Draft 승인] Request Body 사용: reviewerId={}, question={}", 
                requestBody.getReviewerId(), requestBody.getQuestion());
            finalReviewerId = requestBody.getReviewerId();
            finalQuestion = requestBody.getQuestion();
            finalAnswer = requestBody.getAnswer();
        } else {
            // Query Parameter 방식 (하위 호환성)
            log.debug("[FAQ Draft 승인] Query Parameter 사용: reviewerId={}, question={}, answer={}", 
                reviewerId, question, answer);
            
            if (reviewerId == null || question == null || answer == null) {
                String errorMsg = String.format(
                    "reviewerId, question, answer는 필수입니다. (Request Body 또는 Query Parameter로 제공) " +
                    "현재 상태: reviewerId=%s, question=%s, answer=%s",
                    reviewerId != null ? "present" : "null",
                    question != null ? "present" : "null",
                    answer != null ? "present" : "null"
                );
                log.error("[FAQ Draft 승인] 필수 파라미터 누락: {}", errorMsg);
                throw new IllegalArgumentException(errorMsg);
            }
            finalReviewerId = reviewerId;
            finalQuestion = question;
            finalAnswer = answer;
        }
        
        // draftId를 UUID로 변환 시도, 실패하면 faqDraftId로 조회
        UUID draftUuid;
        try {
            draftUuid = UUID.fromString(draftId);
            log.debug("[FAQ Draft 승인] draftId를 UUID로 변환 성공: {}", draftUuid);
        } catch (IllegalArgumentException e) {
            // UUID 형식이 아니면 faqDraftId로 처리
            log.debug("[FAQ Draft 승인] draftId를 UUID로 변환 실패, faqDraftId로 처리: {}", draftId);
            draftUuid = null;
        }
        
        faqDraftService.approve(draftUuid, draftId, finalReviewerId, finalQuestion, finalAnswer);
        
        log.info("[FAQ Draft 승인] 처리 완료: draftId={}", draftId);
    }

    /**
     * 초안 반려 (Request Body 방식 - 권장)
     * 
     * <p>프론트엔드에서 사용하기 편리한 Request Body 방식입니다.
     * 기존 Query Parameter 방식도 하위 호환성을 위해 유지됩니다.</p>
     * 
     * <p>draftId는 UUID 또는 faqDraftId(String) 모두 지원합니다.</p>
     */
    @PostMapping("/{draftId}/reject")
    public void reject(
        @PathVariable String draftId,
        @RequestBody(required = false) FaqDraftRejectRequest requestBody,
        @RequestParam(required = false) UUID reviewerId,
        @RequestParam(required = false) String reason
    ) {
        UUID finalReviewerId;
        String finalReason;
        
        // Request Body가 있으면 우선 사용, 없으면 Query Parameter 사용
        if (requestBody != null) {
            finalReviewerId = requestBody.getReviewerId();
            finalReason = requestBody.getReason();
        } else {
            // Query Parameter 방식 (하위 호환성)
            if (reviewerId == null || reason == null) {
                throw new IllegalArgumentException("reviewerId, reason은 필수입니다. (Request Body 또는 Query Parameter로 제공)");
            }
            finalReviewerId = reviewerId;
            finalReason = reason;
        }
        
        log.info("[FAQ Draft 반려] 요청 수신: draftId={}, reviewerId={}, reason={}", 
            draftId, finalReviewerId, finalReason);
        
        // draftId를 UUID로 변환 시도, 실패하면 faqDraftId로 조회
        UUID draftUuid;
        try {
            draftUuid = UUID.fromString(draftId);
        } catch (IllegalArgumentException e) {
            // UUID 형식이 아니면 faqDraftId로 처리
            draftUuid = null;
        }
        
        faqDraftService.reject(draftUuid, draftId, finalReviewerId, finalReason);
        
        log.info("[FAQ Draft 반려] 처리 완료: draftId={}", draftId);
    }

    /**
     * FAQ Draft 삭제
     * 
     * <p>승인됨 또는 반려됨 상태의 Draft를 삭제합니다.</p>
     * 
     * @param draftId Draft ID (UUID 또는 faqDraftId)
     * @param reviewerId 삭제자 ID (Query Parameter 또는 Request Body)
     */
    @DeleteMapping("/{draftId}")
    public void delete(
        @PathVariable String draftId,
        @RequestParam(required = false) UUID reviewerId
    ) {
        log.info("[FAQ Draft 삭제] 요청 수신: draftId={}, reviewerId={}", draftId, reviewerId);
        
        if (reviewerId == null) {
            throw new IllegalArgumentException("reviewerId는 필수입니다.");
        }
        
        // draftId를 UUID로 변환 시도, 실패하면 faqDraftId로 조회
        UUID draftUuid;
        try {
            draftUuid = UUID.fromString(draftId);
            log.debug("[FAQ Draft 삭제] draftId를 UUID로 변환 성공: {}", draftUuid);
        } catch (IllegalArgumentException e) {
            // UUID 형식이 아니면 faqDraftId로 처리
            log.debug("[FAQ Draft 삭제] draftId를 UUID로 변환 실패, faqDraftId로 처리: {}", draftId);
            draftUuid = null;
        }
        
        faqDraftService.delete(draftUuid, draftId, reviewerId);
        
        log.info("[FAQ Draft 삭제] 처리 완료: draftId={}", draftId);
    }

    /**
     * 자동 FAQ 생성 (질문 로그 기반)
     * 
     * <p>프론트엔드 호환성을 위해 `/admin/faq/drafts/auto-generate` 경로를 제공합니다.</p>
     * <p>실제로는 `/admin/faq/candidates/auto-generate`와 동일한 기능을 수행합니다.</p>
     * 
     * @param request 자동 FAQ 생성 요청
     * @return 자동 FAQ 생성 응답
     */
    @PostMapping("/auto-generate")
    public AutoFaqGenerateResponse generateAuto(
        @Valid @RequestBody AutoFaqGenerateRequest request
    ) {
        log.info("[FAQ 자동 생성] 요청 수신 (drafts 경로): domain={}, minFrequency={}, daysBack={}, maxCandidates={}, autoGenerateDrafts={}",
            request.getDomain(), request.getMinFrequency(), request.getDaysBack(),
            request.getMaxCandidates(), request.getAutoGenerateDrafts());
        
        AutoFaqGenerateResponse response = faqService.generateAuto(request);
        
        log.info("[FAQ 자동 생성] 응답 반환 (drafts 경로): status={}, candidatesFound={}, draftsGenerated={}, draftsFailed={}, draftsCount={}, errorMessage={}",
            response.getStatus(), response.getCandidatesFound(), response.getDraftsGenerated(),
            response.getDraftsFailed(), 
            response.getDrafts() != null ? response.getDrafts().size() : 0,
            response.getErrorMessage());
        
        return response;
    }
}
