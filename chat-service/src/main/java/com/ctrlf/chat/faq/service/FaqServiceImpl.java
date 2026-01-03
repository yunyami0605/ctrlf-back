package com.ctrlf.chat.faq.service;

import com.ctrlf.chat.faq.dto.request.AutoFaqGenerateRequest;
import com.ctrlf.chat.faq.dto.request.FaqCreateRequest;
import com.ctrlf.chat.faq.dto.request.FaqDraftGenerateBatchRequest;
import com.ctrlf.chat.faq.dto.request.FaqDraftGenerateRequest;
import com.ctrlf.chat.faq.dto.request.FaqUpdateRequest;
import com.ctrlf.chat.faq.dto.response.AutoFaqGenerateResponse;
import com.ctrlf.chat.faq.exception.FaqNotFoundException;
import com.ctrlf.chat.faq.dto.response.FaqDraftGenerateBatchResponse;
import com.ctrlf.chat.faq.dto.response.FaqDraftGenerateResponse;
import com.ctrlf.chat.faq.dto.response.FaqResponse;
import com.ctrlf.chat.faq.entity.*;
import com.ctrlf.chat.faq.repository.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
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
    // FAQ 조회 및 관리
    // =========================

    @Override
    public UUID create(FaqCreateRequest request) {
        Faq faq = new Faq();
        faq.setQuestion(request.getQuestion());
        faq.setAnswer(request.getAnswer());
        faq.setDomain(request.getDomain());
        faq.setIsActive(true);
        faq.setPriority(request.getPriority());
        faq.setNeedsRecategorization(false);
        faq.setPublishedAt(Instant.now());
        faq.setCreatedAt(Instant.now());
        faq.setUpdatedAt(Instant.now());

        faqRepository.save(faq);
        log.info("FAQ 수동 생성 완료: id={}, question={}, domain={}", faq.getId(), faq.getQuestion(), faq.getDomain());
        return faq.getId();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FaqResponse> getAll() {
        return faqRepository.findByIsActiveTrueOrderByPriorityAsc()
            .stream()
            .map(FaqResponse::from)
            .toList();
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
                topDocs,  // RAG 검색 결과 전달 (빈 리스트여도 AI 서비스가 처리 가능)
                candidate.getAvgIntentConfidence()  // 평균 의도 신뢰도 전달
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
        faq.setPriority(1);  // 기본 우선순위 설정 (1~5, 기본값: 1)
        faq.setPublishedAt(Instant.now());  // 게시 시각 설정
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

    @Override
    @Transactional
    public FaqDraftGenerateBatchResponse generateDraftBatch(FaqDraftGenerateBatchRequest request) {
        // 요청을 AI 서버 형식으로 변환
        List<FaqAiClient.AiFaqRequest> aiRequests = request.getItems().stream()
            .map(item -> {
                // TopDoc 변환
                List<FaqAiClient.TopDoc> topDocs = null;
                if (item.getTopDocs() != null) {
                    topDocs = item.getTopDocs().stream()
                        .map(doc -> new FaqAiClient.TopDoc(
                            doc.getDocId(),
                            doc.getDocVersion(),
                            doc.getTitle(),
                            doc.getSnippet(),
                            doc.getArticleLabel(),
                            doc.getArticlePath(),
                            doc.getScore(),
                            doc.getPage(),
                            doc.getDataset(),
                            doc.getSource()
                        ))
                        .collect(Collectors.toList());
                }

                // Domain을 RAGFlow가 지원하는 dataset 값으로 매핑
                String mappedDomain = mapDomainToRagflowDataset(item.getDomain());

                return new FaqAiClient.AiFaqRequest(
                    mappedDomain,
                    item.getClusterId(),
                    item.getCanonicalQuestion(),
                    item.getSampleQuestions(),
                    topDocs,
                    item.getAvgIntentConfidence()
                );
            })
            .collect(Collectors.toList());

        // AI 서버에 배치 요청
        FaqAiClient.AiFaqBatchResponse aiResponse = faqAiClient.generateBatch(aiRequests);

        // 응답을 백엔드 형식으로 변환
        List<FaqDraftGenerateResponse> responses = aiResponse.items().stream()
            .map(aiItem -> {
                FaqDraftGenerateResponse.FaqDraftPayload payload = null;
                if (aiItem.faq_draft() != null) {
                    payload = new FaqDraftGenerateResponse.FaqDraftPayload(
                        aiItem.faq_draft().faq_draft_id(),
                        aiItem.faq_draft().domain(),
                        aiItem.faq_draft().cluster_id(),
                        aiItem.faq_draft().question(),
                        aiItem.faq_draft().answer_markdown(),
                        aiItem.faq_draft().summary(),
                        aiItem.faq_draft().source_doc_id(),
                        aiItem.faq_draft().source_doc_version(),
                        aiItem.faq_draft().source_article_label(),
                        aiItem.faq_draft().source_article_path(),
                        aiItem.faq_draft().answer_source(),
                        aiItem.faq_draft().ai_confidence(),
                        aiItem.faq_draft().created_at()
                    );
                }

                return new FaqDraftGenerateResponse(
                    aiItem.status(),
                    payload,
                    aiItem.error_message()
                );
            })
            .collect(Collectors.toList());

        // 성공한 항목들을 DB에 저장
        for (int i = 0; i < responses.size(); i++) {
            FaqDraftGenerateResponse response = responses.get(i);
            if ("SUCCESS".equals(response.getStatus()) && response.getFaqDraft() != null) {
                FaqDraftGenerateRequest originalRequest = request.getItems().get(i);
                FaqDraftGenerateResponse.FaqDraftPayload draft = response.getFaqDraft();

                FaqDraft faqDraft = FaqDraft.builder()
                    .faqDraftId(draft.getFaqDraftId())
                    .domain(originalRequest.getDomain())  // 원본 domain 사용
                    .clusterId(draft.getClusterId())
                    .question(draft.getQuestion())
                    .answerMarkdown(draft.getAnswerMarkdown())
                    .summary(draft.getSummary())
                    .aiConfidence(draft.getAiConfidence())
                    .status(FaqDraft.Status.DRAFT)
                    .createdAt(java.time.LocalDateTime.now())
                    .build();

                faqDraftRepository.save(faqDraft);
            }
        }

        return new FaqDraftGenerateBatchResponse(
            responses,
            aiResponse.total_count(),
            aiResponse.success_count(),
            aiResponse.failed_count()
        );
    }

    @Override
    @Transactional
    public AutoFaqGenerateResponse generateAuto(AutoFaqGenerateRequest request) {
        // 기본값 설정
        String domain = request.getDomain();
        Integer minFrequency = request.getMinFrequency() != null ? request.getMinFrequency() : 3;
        Integer daysBack = request.getDaysBack() != null ? request.getDaysBack() : 30;
        Integer maxCandidates = request.getMaxCandidates() != null ? request.getMaxCandidates() : 20;
        Boolean autoGenerateDrafts = request.getAutoGenerateDrafts() != null ? request.getAutoGenerateDrafts() : true;

        // AI 서버에 자동 FAQ 생성 요청 (camelCase로 전송)
        FaqAiClient.AutoFaqGenerateRequest aiRequest = new FaqAiClient.AutoFaqGenerateRequest(
            domain,
            minFrequency,  // minFrequency
            daysBack,  // daysBack
            maxCandidates,  // maxCandidates
            autoGenerateDrafts  // autoGenerateDrafts
        );

        FaqAiClient.AutoFaqGenerateResponse aiResponse;
        try {
            aiResponse = faqAiClient.generateAuto(aiRequest);
        } catch (Exception e) {
            return new AutoFaqGenerateResponse(
                "FAILED",
                0,
                0,
                0,
                java.util.Collections.emptyList(),
                String.format("AI 서비스 호출 실패: %s", e.getMessage())
            );
        }

        // AI 서버에서 받은 FAQ 초안들을 DB에 저장
        int savedCount = 0;
        int failedCount = 0;
        java.util.List<com.ctrlf.chat.faq.dto.response.FaqDraftGenerateResponse.FaqDraftPayload> savedDrafts =
            new java.util.ArrayList<>();

        // candidates 정보는 로깅용으로만 사용 (DB 저장은 하지 않음)
        if (aiResponse.candidates() != null && !aiResponse.candidates().isEmpty()) {
            log.info("AI 서버에서 {}개의 FAQ 후보를 발견했습니다", aiResponse.candidates().size());
        }

        if (aiResponse.drafts() != null && !aiResponse.drafts().isEmpty()) {
            for (FaqAiClient.FaqDraftPayload draftPayload : aiResponse.drafts()) {
                try {
                    // Domain을 원본 형식으로 매핑 (AI 서버는 SEC_POLICY 형식 사용)
                    String originalDomain = mapDomainFromRagflowDataset(draftPayload.domain());

                    FaqDraft faqDraft = FaqDraft.builder()
                        .faqDraftId(draftPayload.faq_draft_id())
                        .domain(originalDomain)
                        .clusterId(draftPayload.cluster_id())
                        .question(draftPayload.question())
                        .answerMarkdown(draftPayload.answer_markdown())
                        .summary(draftPayload.summary())
                        .aiConfidence(draftPayload.ai_confidence())
                        .status(FaqDraft.Status.DRAFT)
                        .createdAt(java.time.LocalDateTime.now())
                        .build();

                    faqDraftRepository.save(faqDraft);
                    savedCount++;

                    // 응답용 DTO 생성
                    savedDrafts.add(new com.ctrlf.chat.faq.dto.response.FaqDraftGenerateResponse.FaqDraftPayload(
                        draftPayload.faq_draft_id(),
                        originalDomain,
                        draftPayload.cluster_id(),
                        draftPayload.question(),
                        draftPayload.answer_markdown(),
                        draftPayload.summary(),
                        draftPayload.source_doc_id(),
                        draftPayload.source_doc_version(),
                        draftPayload.source_article_label(),
                        draftPayload.source_article_path(),
                        draftPayload.answer_source(),
                        draftPayload.ai_confidence(),
                        draftPayload.created_at()
                    ));
                } catch (Exception e) {
                    failedCount++;
                    // 로그만 남기고 계속 진행
                }
            }
        }

        return new AutoFaqGenerateResponse(
            aiResponse.status(),
            aiResponse.candidates_found(),
            savedCount,
            failedCount + aiResponse.drafts_failed(),
            savedDrafts,
            aiResponse.error_message()
        );
    }

    /**
     * RAGFlow dataset 값을 원본 domain 형식으로 역매핑
     *
     * @param ragflowDomain RAGFlow dataset 값 (예: "SEC_POLICY", "POLICY")
     * @return 원본 domain 값 (예: "SECURITY", "POLICY")
     */
    private String mapDomainFromRagflowDataset(String ragflowDomain) {
        if (ragflowDomain == null || ragflowDomain.isBlank()) {
            return "SECURITY";  // 기본값
        }

        String upperDomain = ragflowDomain.toUpperCase();

        // SEC_POLICY → SECURITY
        if ("SEC_POLICY".equals(upperDomain)) {
            return "SECURITY";
        }

        // POLICY, TEST는 그대로 사용
        if ("POLICY".equals(upperDomain) || "TEST".equals(upperDomain)) {
            return upperDomain;
        }

        // 그 외는 그대로 반환
        return ragflowDomain;
    }
}
