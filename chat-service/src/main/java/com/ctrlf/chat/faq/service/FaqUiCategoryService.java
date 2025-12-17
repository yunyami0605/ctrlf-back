package com.ctrlf.chat.faq.service;

import com.ctrlf.chat.faq.dto.request.FaqUiCategoryCreateRequest;
import com.ctrlf.chat.faq.dto.request.FaqUiCategoryUpdateRequest;
import com.ctrlf.chat.faq.dto.response.FaqUiCategoryResponse;
import java.util.List;
import java.util.UUID;

/**
 * FAQ 화면(UI) 카테고리 관리 서비스
 */
public interface FaqUiCategoryService {

    /**
     * FAQ UI 카테고리 생성
     */
    UUID create(FaqUiCategoryCreateRequest req, UUID operatorId);

    /**
     * FAQ UI 카테고리 수정
     */
    void update(UUID categoryId, FaqUiCategoryUpdateRequest req, UUID operatorId);

    /**
     * FAQ UI 카테고리 비활성화
     */
    void deactivate(UUID categoryId, UUID operatorId, String reason);

    /**
     * 활성화된 UI 카테고리 목록 조회
     */
    List<FaqUiCategoryResponse> getActiveCategories();

    /**
     * 전체 UI 카테고리 목록 조회 (비활성 포함)
     */
    List<FaqUiCategoryResponse> getAllCategories();
}
