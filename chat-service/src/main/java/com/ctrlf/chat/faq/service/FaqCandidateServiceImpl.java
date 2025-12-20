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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        List<FaqCandidate> candidates;

        if (domain != null && status != null) {
            candidates = faqCandidateRepository.findByDomainAndStatus(
                domain,
                FaqCandidate.CandidateStatus.valueOf(status)
            );
        } else if (domain != null) {
            candidates = faqCandidateRepository.findByDomain(domain);
        } else {
            candidates = faqCandidateRepository.findAll();
        }

        return candidates.stream()
            .map(FaqCandidateResponse::from)
            .toList();
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
