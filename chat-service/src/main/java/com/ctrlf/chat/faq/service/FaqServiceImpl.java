package com.ctrlf.chat.faq.service;

import com.ctrlf.chat.ai.search.domain.SearchDataset;
import com.ctrlf.chat.ai.search.facade.SearchFacade;
import com.ctrlf.chat.ai.search.dto.AiSearchResponse;
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

    // 🔹 RAG Search 연동 (이번 작업의 핵심)
    private final SearchFacade searchFacade;

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

    @Override
    public UUID generateDraftFromCandidate(UUID candidateId) {
        FaqCandidate candidate = faqCandidateRepository.findById(candidateId)
            .orElseThrow(() -> new IllegalArgumentException("FAQ 후보가 존재하지 않습니다."));

        // PII / 의도 신뢰도 정책
        if (Boolean.TRUE.equals(candidate.getPiiDetected())) {
            candidate.setStatus(FaqCandidate.CandidateStatus.EXCLUDED);
            throw new IllegalArgumentException("PII가 감지된 FAQ 후보는 Draft를 생성할 수 없습니다.");
        }

        if (candidate.getAvgIntentConfidence() == null || candidate.getAvgIntentConfidence() < 0.7) {
            candidate.setStatus(FaqCandidate.CandidateStatus.EXCLUDED);
            throw new IllegalArgumentException(
                String.format("의도 신뢰도가 부족합니다. (현재: %s, 최소 요구: 0.7)", 
                    candidate.getAvgIntentConfidence())
            );
        }

        // ======================================
        // 🔹 RAG 검색 연동 (LLM 미사용)
        // ======================================
        List<AiSearchResponse.Result> topDocs =
            searchFacade.searchDocs(
                candidate.getCanonicalQuestion(),
                SearchDataset.POLICY,
                5
            );

        // TODO
        // 1. topDocs → top_docs DTO 변환
        // 2. /ai/faq/generate 호출 시 top_docs 전달
        // (AI 서버 완성 후 구현)

        // ======================================
        // 기존 AI FAQ 생성 로직 유지
        // ======================================
        FaqAiClient.AiFaqResponse aiResponse =
            faqAiClient.generate(
                candidate.getDomain(),
                candidate.getId().toString(), // cluster_id 대체
                candidate.getCanonicalQuestion()
            );

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
