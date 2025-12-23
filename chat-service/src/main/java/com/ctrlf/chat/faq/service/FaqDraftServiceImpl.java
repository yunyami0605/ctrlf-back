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
            // status 파라미터 검증 및 변환 (PENDING -> DRAFT)
            FaqDraft.Status statusEnum = parseStatus(status);
            drafts = faqDraftRepository.findByDomainAndStatus(domain, statusEnum);
        } else if (status != null) {
            FaqDraft.Status statusEnum = parseStatus(status);
            drafts = faqDraftRepository.findByStatus(statusEnum);
        } else if (domain != null) {
            drafts = faqDraftRepository.findByDomain(domain);
        } else {
            drafts = faqDraftRepository.findAll();
        }

        return drafts.stream()
            .map(FaqDraftResponse::from)
            .toList();
    }

    /**
     * status 문자열을 FaqDraft.Status enum으로 변환합니다.
     * PENDING은 DRAFT로 매핑됩니다.
     */
    private FaqDraft.Status parseStatus(String status) {
        if (status == null) {
            return null;
        }
        String upperStatus = status.toUpperCase();
        // PENDING은 DRAFT로 매핑 (AI 서버에서 사용하는 상태값)
        if ("PENDING".equals(upperStatus)) {
            return FaqDraft.Status.DRAFT;
        }
        try {
            return FaqDraft.Status.valueOf(upperStatus);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                String.format("유효하지 않은 status 값입니다: %s (가능한 값: DRAFT, PUBLISHED, REJECTED, PENDING)", status)
            );
        }
    }

    @Override
    public void approve(UUID draftId, UUID reviewerId, String question, String answer) {
        FaqDraft draft = faqDraftRepository.findById(draftId)
            .orElseThrow(() -> new IllegalArgumentException("FAQ 초안이 없습니다."));

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

        // 이미 승인된 Draft는 반려할 수 없음
        if (draft.getStatus() == FaqDraft.Status.PUBLISHED) {
            throw new IllegalStateException("이미 승인된 FAQ 초안은 반려할 수 없습니다.");
        }

        // 이미 반려된 Draft는 다시 반려할 수 없음
        if (draft.getStatus() == FaqDraft.Status.REJECTED) {
            throw new IllegalStateException("이미 반려된 FAQ 초안입니다.");
        }

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
