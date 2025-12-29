package com.ctrlf.chat.faq.service;

import com.ctrlf.chat.faq.dto.request.FaqCreateRequest;
import com.ctrlf.chat.faq.dto.request.FaqUpdateRequest;
import com.ctrlf.chat.faq.dto.response.FaqResponse;
import com.ctrlf.chat.faq.entity.*;
import com.ctrlf.chat.faq.exception.FaqNotFoundException;
import com.ctrlf.chat.faq.repository.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class FaqServiceImpl implements FaqService {

    private final FaqRepository faqRepository;
    private final FaqCandidateRepository faqCandidateRepository;
    private final FaqDraftRepository faqDraftRepository;
    private final FaqRevisionRepository faqRevisionRepository;
    private final FaqAiClient faqAiClient;

    // =========================
    // 기존 FAQ CRUD
    // =========================

    @Override
    public UUID create(FaqCreateRequest request) {
        Instant now = Instant.now();
        Faq faq = new Faq();
        faq.setQuestion(request.getQuestion());
        faq.setAnswer(request.getAnswer());
        faq.setDomain(request.getDomain());
        faq.setPriority(request.getPriority());
        faq.setIsActive(true);
        faq.setNeedsRecategorization(false); // 기본값 설정
        faq.setPublishedAt(now); // 기본값 설정
        faq.setCreatedAt(now);
        faq.setUpdatedAt(now);

        return faqRepository.save(faq).getId();
    }

    @Override
    public void update(UUID id, FaqUpdateRequest request) {
        Faq faq = faqRepository.findById(id)
            .orElseThrow(() -> new FaqNotFoundException(id));

        if (request.getQuestion() != null) faq.setQuestion(request.getQuestion());
        if (request.getAnswer() != null) faq.setAnswer(request.getAnswer());
        if (request.getDomain() != null) faq.setDomain(request.getDomain());
        if (request.getIsActive() != null) faq.setIsActive(request.getIsActive());
        if (request.getPriority() != null) faq.setPriority(request.getPriority());

        faq.setUpdatedAt(Instant.now());
    }

    @Override
    public void delete(UUID id) {
        Faq faq = faqRepository.findById(id)
            .orElseThrow(() -> new FaqNotFoundException(id));

        faq.setIsActive(false);
        faq.setUpdatedAt(Instant.now());
    }

    @Override
    @Transactional(readOnly = true)
    public List<FaqResponse> getAll() {
        return faqRepository.findByIsActiveTrueOrderByPriorityAsc()
            .stream()
            .map(FaqResponse::from)
            .toList();
    }

    // =========================
    // FAQ 자동 생성 연계
    // =========================

    /**
     * Domain을 RAGFlow가 지원하는 dataset 값으로 매핑
     *
     * AI 서비스가 기대하는 domain 형식으로 매핑합니다.
     * - SECURITY -> SEC_POLICY
     * - EDUCATION -> SEC_POLICY (또는 적절한 매핑)
     * - POLICY -> POLICY
     *
     * @param domain 원본 domain (예: "SECURITY", "EDUCATION", "POLICY" 등)
     * @return AI 서비스가 기대하는 dataset 값
     */
    private String mapDomainToRagflowDataset(String domain) {
        if (domain == null || domain.isBlank()) {
            return "SEC_POLICY";  // 기본값을 SEC_POLICY로 변경
        }

        // 대소문자 무시하고 매핑
        String upperDomain = domain.toUpperCase();

        // SECURITY 관련 도메인은 SEC_POLICY로 매핑
        if ("SECURITY".equals(upperDomain) || "SEC_POLICY".equals(upperDomain)) {
            return "SEC_POLICY";
        }

        // POLICY는 그대로 사용
        if ("POLICY".equals(upperDomain)) {
            return "POLICY";
        }

        // TEST는 그대로 사용
        if ("TEST".equals(upperDomain)) {
            return "TEST";
        }

        // 그 외의 모든 domain은 SEC_POLICY로 매핑 (EDUCATION 등)
        // AI 서비스에서 SEC_POLICY로 성공했으므로 기본값으로 사용
        return "SEC_POLICY";
    }

    @Override
    public UUID generateDraftFromCandidate(UUID candidateId) {
        FaqCandidate candidate = faqCandidateRepository.findById(candidateId)
            .orElseThrow(() -> new IllegalArgumentException(
                String.format("FAQ 후보가 존재하지 않습니다. candidateId=%s", candidateId)
            ));

        // PII / 의도 신뢰도 정책
        if (Boolean.TRUE.equals(candidate.getPiiDetected())) {
            candidate.setStatus(FaqCandidate.CandidateStatus.EXCLUDED);
            faqCandidateRepository.save(candidate);
            throw new IllegalArgumentException("PII가 감지된 FAQ 후보는 Draft를 생성할 수 없습니다.");
        }

        // 의도 신뢰도 검증 (테스트 환경에서는 완화 가능)
        double minConfidence = 0.7;
        // TODO: 프로파일별로 조정 가능 (예: local 프로파일에서는 0.5로 완화)
        if (candidate.getAvgIntentConfidence() == null || candidate.getAvgIntentConfidence() < minConfidence) {
            candidate.setStatus(FaqCandidate.CandidateStatus.EXCLUDED);
            faqCandidateRepository.save(candidate);
            throw new IllegalArgumentException(
                String.format("의도 신뢰도가 부족합니다. (현재: %s, 최소 요구: %.1f)",
                    candidate.getAvgIntentConfidence(), minConfidence)
            );
        }

        // ======================================
        // 🔹 RAG 검색은 AI 서버에서 직접 처리
        // AI 서버의 FAQ 생성 API가 RAGFlow를 직접 호출하므로
        // chat-service에서는 빈 topDocs를 전달
        // ======================================
        List<FaqAiClient.TopDoc> topDocs = java.util.Collections.emptyList();

        // ======================================
        // AI 서비스 호출 (RAG + LLM을 사용한 FAQ 초안 생성)
        // ======================================
        // ⚠️ AI 서비스가 domain을 RAGFlow dataset으로 사용하므로,
        // RAGFlow가 지원하는 값으로 매핑 (POLICY, TEST 등)
        // 현재 RAGFlow는 'POLICY', 'TEST'만 지원하므로, 모든 domain을 'POLICY'로 매핑
        String mappedDomain = mapDomainToRagflowDataset(candidate.getDomain());

        FaqAiClient.AiFaqResponse aiResponse;
        try {
            // sample_questions는 현재 candidate에서 가져올 수 없으므로 null 전달
            // 향후 candidate에 sample_questions 필드가 추가되면 활용 가능
            List<String> sampleQuestions = null;

            aiResponse = faqAiClient.generate(
                mappedDomain,  // RAGFlow가 지원하는 dataset 값으로 매핑
                candidate.getId().toString(), // cluster_id 대체
                candidate.getCanonicalQuestion(),
                sampleQuestions,  // 샘플 질문 목록 (선택, 현재는 null)
                topDocs  // RAG 검색 결과 전달 (빈 리스트여도 AI 서비스가 처리 가능)
            );
        } catch (IllegalStateException e) {
            // 이미 상세한 에러 메시지가 포함된 예외는 그대로 전파
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                String.format("AI 서비스 호출 실패: candidateId=%s, domain=%s, mappedDomain=%s, topDocsCount=%d, error=%s",
                    candidateId, candidate.getDomain(), mappedDomain, topDocs.size(), e.getMessage()),
                e
            );
        }

        // AI 서비스 응답 검증
        if (!"SUCCESS".equals(aiResponse.status()) || aiResponse.faq_draft() == null) {
            String errorMsg = aiResponse.error_message() != null
                ? aiResponse.error_message()
                : "AI 서비스에서 FAQ 초안 생성에 실패했습니다.";
            throw new IllegalStateException(
                String.format("FAQ 초안 생성 실패: candidateId=%s, error=%s, status=%s",
                    candidateId, errorMsg, aiResponse.status())
            );
        }

        FaqDraft draft = FaqDraft.builder()
            .faqDraftId(aiResponse.faq_draft().faq_draft_id())
            .domain(candidate.getDomain())
            .clusterId(candidate.getId().toString())
            .question(aiResponse.faq_draft().question())
            .answerMarkdown(aiResponse.faq_draft().answer_markdown())
            .summary(aiResponse.faq_draft().summary())
            .aiConfidence(aiResponse.faq_draft().ai_confidence())
            .status(FaqDraft.Status.DRAFT)
            .createdAt(java.time.LocalDateTime.now())
            .build();

        faqDraftRepository.save(draft);
        return draft.getId();
    }

    @Override
    public void approveDraft(UUID draftId, UUID reviewerId, String question, String answer) {
        FaqDraft draft = faqDraftRepository.findById(draftId)
            .orElseThrow(() -> new IllegalArgumentException("FAQ 초안이 존재하지 않습니다."));

        // 이미 승인된 Draft는 다시 승인할 수 없음
        if (draft.getStatus() == FaqDraft.Status.PUBLISHED) {
            throw new IllegalStateException("이미 승인된 FAQ 초안입니다.");
        }

        // 이미 반려된 Draft는 승인할 수 없음
        if (draft.getStatus() == FaqDraft.Status.REJECTED) {
            throw new IllegalStateException("반려된 FAQ 초안은 승인할 수 없습니다.");
        }

        // 게시 FAQ 생성
        Faq faq = new Faq();
        faq.setQuestion(question);
        faq.setAnswer(answer);
        faq.setDomain(draft.getDomain());
        faq.setIsActive(true);
        faq.setNeedsRecategorization(false);  // 기본값 설정
        faq.setCreatedAt(Instant.now());
        faq.setUpdatedAt(Instant.now());

        faqRepository.save(faq);

        // 초안 상태 변경
        draft.publish(reviewerId);

        // 관리자 이력
        FaqRevision revision = FaqRevision.create(
            "FAQ_DRAFT",
            draft.getId(),
            "APPROVE",
            reviewerId,
            null
        );

        faqRevisionRepository.save(revision);
    }

    @Override
    public void rejectDraft(UUID draftId, UUID reviewerId, String reason) {
        FaqDraft draft = faqDraftRepository.findById(draftId)
            .orElseThrow(() -> new IllegalArgumentException("FAQ 초안이 존재하지 않습니다."));

        // 이미 승인된 Draft는 반려할 수 없음
        if (draft.getStatus() == FaqDraft.Status.PUBLISHED) {
            throw new IllegalStateException("이미 승인된 FAQ 초안은 반려할 수 없습니다.");
        }

        // 이미 반려된 Draft는 다시 반려할 수 없음
        if (draft.getStatus() == FaqDraft.Status.REJECTED) {
            throw new IllegalStateException("이미 반려된 FAQ 초안입니다.");
        }

        draft.reject(reviewerId);

        FaqRevision revision = FaqRevision.create(
            "FAQ_DRAFT",
            draft.getId(),
            "REJECT",
            reviewerId,
            reason
        );

        faqRevisionRepository.save(revision);
    }
}
