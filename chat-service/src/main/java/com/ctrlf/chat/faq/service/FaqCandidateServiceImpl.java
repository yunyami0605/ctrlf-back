package com.ctrlf.chat.faq.service;

import com.ctrlf.chat.faq.dto.request.FaqCandidateCreateRequest;
import com.ctrlf.chat.faq.dto.response.FaqCandidateResponse;
import com.ctrlf.chat.faq.entity.FaqCandidate;
import com.ctrlf.chat.faq.entity.FaqRevision;
import com.ctrlf.chat.faq.repository.FaqCandidateRepository;
import com.ctrlf.chat.faq.repository.FaqRevisionRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FaqCandidateServiceImpl implements FaqCandidateService {

    private final FaqCandidateRepository faqCandidateRepository;
    private final FaqRevisionRepository faqRevisionRepository;

    @Override
    public UUID create(FaqCandidateCreateRequest request) {
        FaqCandidate candidate = new FaqCandidate();
        candidate.setCanonicalQuestion(request.getQuestion());
        candidate.setDomain(request.getDomain());
        candidate.setStatus(FaqCandidate.CandidateStatus.ELIGIBLE);
        candidate.setPiiDetected(false);
        candidate.setQuestionCount7d(0);
        candidate.setQuestionCount30d(0);
        // 테스트/개발용: 기본 의도 신뢰도 설정 (실제 운영에서는 AI 서버나 분석 로직에서 계산되어야 함)
        candidate.setAvgIntentConfidence(0.8);
        candidate.setCreatedAt(Instant.now());
        
        return faqCandidateRepository.save(candidate).getId();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FaqCandidateResponse> getCandidates(String domain, String status) {
        log.info("[FAQ 후보 목록 조회] 서비스 호출: domain={}, status={}", domain, status);
        
        List<FaqCandidate> candidates;

        try {
            if (domain != null && status != null) {
                log.debug("[FAQ 후보 목록 조회] 도메인+상태로 조회: domain={}, status={}", domain, status);
                FaqCandidate.CandidateStatus candidateStatus = FaqCandidate.CandidateStatus.valueOf(status.toUpperCase());
                candidates = faqCandidateRepository.findByDomainAndStatus(domain, candidateStatus);
            } else if (domain != null) {
                log.debug("[FAQ 후보 목록 조회] 도메인으로 조회: domain={}", domain);
                candidates = faqCandidateRepository.findByDomain(domain);
            } else if (status != null) {
                log.debug("[FAQ 후보 목록 조회] 상태로 조회: status={}", status);
                FaqCandidate.CandidateStatus candidateStatus = FaqCandidate.CandidateStatus.valueOf(status.toUpperCase());
                candidates = faqCandidateRepository.findAll().stream()
                    .filter(c -> c.getStatus() == candidateStatus)
                    .toList();
            } else {
                log.debug("[FAQ 후보 목록 조회] 전체 조회");
                candidates = faqCandidateRepository.findAll();
            }
            
            log.info("[FAQ 후보 목록 조회] DB 조회 완료: totalCount={}", candidates.size());
            
            if (!candidates.isEmpty()) {
                log.debug("[FAQ 후보 목록 조회] 조회된 후보 샘플 (최대 3개): {}", 
                    candidates.stream()
                        .limit(3)
                        .map(c -> String.format("{id=%s, question='%s', domain=%s, status=%s}", 
                            c.getId(),
                            c.getCanonicalQuestion() != null && c.getCanonicalQuestion().length() > 50 
                                ? c.getCanonicalQuestion().substring(0, 50) + "..." 
                                : c.getCanonicalQuestion(),
                            c.getDomain(),
                            c.getStatus()))
                        .toList());
            }
        } catch (IllegalArgumentException e) {
            log.error("[FAQ 후보 목록 조회] 잘못된 status 값: status={}, error={}", status, e.getMessage());
            // 잘못된 status 값이면 전체 조회로 fallback
            candidates = faqCandidateRepository.findAll();
            log.info("[FAQ 후보 목록 조회] 잘못된 status로 인해 전체 조회로 변경: totalCount={}", candidates.size());
        }

        List<FaqCandidateResponse> response = candidates.stream()
            .map(FaqCandidateResponse::from)
            .toList();
        
        log.info("[FAQ 후보 목록 조회] DTO 변환 완료: responseCount={}", response.size());
        
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public FaqCandidateResponse getCandidate(UUID id) {
        FaqCandidate candidate = faqCandidateRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("FAQ 후보가 존재하지 않습니다."));

        return FaqCandidateResponse.from(candidate);
    }

    @Override
    public void excludeCandidate(UUID id, String reason) {
        FaqCandidate candidate = faqCandidateRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("FAQ 후보가 존재하지 않습니다."));

        // 후보 상태 변경
        candidate.setStatus(FaqCandidate.CandidateStatus.EXCLUDED);
        faqCandidateRepository.save(candidate);

        // 관리자 수정 이력 기록 (정적 팩토리 사용)
        FaqRevision revision = FaqRevision.create(
            "FAQ_CANDIDATE",      // targetType
            candidate.getId(),    // targetId
            "EXCLUDE",            // action
            null,                 // actorId (Keycloak 연동 전)
            reason                // 사유
        );

        faqRevisionRepository.save(revision);
    }
}
