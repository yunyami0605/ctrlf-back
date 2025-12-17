package com.ctrlf.chat.faq.service;

import com.ctrlf.chat.faq.dto.request.FaqUiCategoryCreateRequest;
import com.ctrlf.chat.faq.dto.request.FaqUiCategoryUpdateRequest;
import com.ctrlf.chat.faq.dto.response.FaqUiCategoryResponse;
import com.ctrlf.chat.faq.entity.Faq;
import com.ctrlf.chat.faq.entity.FaqRevision;
import com.ctrlf.chat.faq.entity.FaqUiCategory;
import com.ctrlf.chat.faq.repository.FaqRepository;
import com.ctrlf.chat.faq.repository.FaqRevisionRepository;
import com.ctrlf.chat.faq.repository.FaqUiCategoryRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class FaqUiCategoryServiceImpl implements FaqUiCategoryService {

    private static final String ETC_SLUG = "ETC";

    private final FaqUiCategoryRepository faqUiCategoryRepository;
    private final FaqRepository faqRepository;
    private final FaqRevisionRepository faqRevisionRepository;

    @Override
    public UUID create(FaqUiCategoryCreateRequest req, UUID operatorId) {
        if (req.getSlug() == null || req.getSlug().isBlank()) {
            throw new IllegalArgumentException("slug는 필수입니다.");
        }
        if (req.getDisplayName() == null || req.getDisplayName().isBlank()) {
            throw new IllegalArgumentException("displayName은 필수입니다.");
        }

        faqUiCategoryRepository.findBySlug(req.getSlug())
            .ifPresent(x -> { throw new IllegalArgumentException("이미 존재하는 slug 입니다."); });

        int sortOrder = (req.getSortOrder() != null) ? req.getSortOrder() : 999;

        FaqUiCategory category = FaqUiCategory.create(
            UUID.randomUUID(),
            req.getSlug(),
            req.getDisplayName(),
            sortOrder,
            operatorId
        );

        return faqUiCategoryRepository.save(category).getId();
    }

    @Override
    public void update(UUID categoryId, FaqUiCategoryUpdateRequest req, UUID operatorId) {
        FaqUiCategory category = faqUiCategoryRepository.findById(categoryId)
            .orElseThrow(() -> new IllegalArgumentException("카테고리가 존재하지 않습니다."));

        category.update(req.getDisplayName(), req.getSortOrder(), req.getIsActive(), operatorId);
    }

    // ===============================
    // ✅ 카테고리 비활성화 + FAQ 이동
    // ===============================
    @Override
    public void deactivate(UUID categoryId, UUID operatorId, String reason) {
        FaqUiCategory category = faqUiCategoryRepository.findById(categoryId)
            .orElseThrow(() -> new IllegalArgumentException("카테고리가 존재하지 않습니다."));

        if (!category.getIsActive()) return;

        FaqUiCategory etc = faqUiCategoryRepository.findBySlug(ETC_SLUG)
            .orElseThrow(() -> new IllegalStateException("ETC 카테고리가 존재하지 않습니다."));

        category.setIsActive(false);

        List<Faq> faqs = faqRepository.findByUiCategoryId(categoryId);
        for (Faq faq : faqs) {
            faq.setUiCategoryId(etc.getId());
            faq.setNeedsRecategorization(true);
        }

        faqRevisionRepository.save(
            FaqRevision.create(
                "UI_CATEGORY",
                categoryId,
                "DEACTIVATE",
                operatorId,
                reason
            )
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<FaqUiCategoryResponse> getActiveCategories() {
        return faqUiCategoryRepository.findByIsActiveTrueOrderBySortOrderAsc()
            .stream().map(FaqUiCategoryResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FaqUiCategoryResponse> getAllCategories() {
        return faqUiCategoryRepository.findAllByOrderBySortOrderAsc()
            .stream().map(FaqUiCategoryResponse::from).toList();
    }
}
