package com.ctrlf.chat.faq.service;

import com.ctrlf.chat.faq.dto.response.FaqDraftResponse;
import com.ctrlf.chat.faq.entity.*;
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
public class FaqDraftServiceImpl implements FaqDraftService {

    private final FaqDraftRepository faqDraftRepository;
    private final FaqRepository faqRepository;
    private final FaqRevisionRepository faqRevisionRepository;

    @Override
    @Transactional(readOnly = true)
    public List<FaqDraftResponse> getDrafts(String domain, String status) {
        List<FaqDraft> drafts;

        if (domain != null && status != null) {
            drafts = faqDraftRepository.findByDomainAndStatus(
                domain,
                FaqDraft.Status.valueOf(status)
            );
        } else if (status != null) {
            drafts = faqDraftRepository.findByStatus(FaqDraft.Status.valueOf(status));
        } else if (domain != null) {
            drafts = faqDraftRepository.findByDomain(domain);
        } else {
            drafts = faqDraftRepository.findAll();
        }

        return drafts.stream()
            .map(FaqDraftResponse::from)
            .toList();
    }

    @Override
    public void approve(UUID draftId, UUID reviewerId, String question, String answer) {
        FaqDraft draft = faqDraftRepository.findById(draftId)
            .orElseThrow(() -> new IllegalArgumentException("FAQ 초안이 없습니다."));

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

        // 관리자 이력 저장
        faqRevisionRepository.save(
            FaqRevision.create(
                "FAQ_DRAFT",
                draft.getId(),
                "APPROVE",
                reviewerId,
                null
            )
        );
    }

    @Override
    public void reject(UUID draftId, UUID reviewerId, String reason) {
        FaqDraft draft = faqDraftRepository.findById(draftId)
            .orElseThrow(() -> new IllegalArgumentException("FAQ 초안이 없습니다."));

        draft.reject(reviewerId);

        faqRevisionRepository.save(
            FaqRevision.create(
                "FAQ_DRAFT",
                draft.getId(),
                "REJECT",
                reviewerId,
                reason
            )
        );
    }
}
