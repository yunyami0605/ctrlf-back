package com.ctrlf.chat.faq.repository;

import com.ctrlf.chat.faq.entity.Faq;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FaqRepository extends JpaRepository<Faq, UUID> {

    List<Faq> findByIsActiveTrueOrderByPriorityAsc();
    
    /**
     * 모든 활성화된 FAQ 조회 (초기 데이터 + 관리자 승인 FAQ 모두 포함)
     * 우선순위 순으로 정렬, 동일 우선순위는 publishedAt DESC, createdAt ASC
     */
    @Query("""
        SELECT f
        FROM Faq f
        WHERE f.isActive = true
        ORDER BY f.priority ASC, f.publishedAt DESC, f.createdAt ASC
        """)
    List<Faq> findAllActiveOrderedByPriorityAndPublishedAt();

    List<Faq> findTop10ByDomainAndIsActiveTrueOrderByPublishedAtDesc(String domain);

    Optional<Faq> findByQuestionAndDomain(String question, String domain);

    // ✅ 카테고리 비활성화 시 FAQ 이동용
    List<Faq> findByUiCategoryId(UUID uiCategoryId);

    @Query("""
        SELECT f
        FROM Faq f
        WHERE f.isActive = true
        ORDER BY f.domain, f.publishedAt DESC, f.priority ASC, f.createdAt ASC
        """)
    List<Faq> findAllActiveOrderedByDomainAndPublishedAt();
}
