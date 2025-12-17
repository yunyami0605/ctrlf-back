package com.ctrlf.chat.faq.repository;

import com.ctrlf.chat.faq.entity.Faq;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FaqRepository extends JpaRepository<Faq, UUID> {

    List<Faq> findByIsActiveTrueOrderByPriorityAsc();

    List<Faq> findTop10ByDomainAndIsActiveTrueOrderByPublishedAtDesc(String domain);

    Optional<Faq> findByQuestionAndDomain(String question, String domain);

    // ✅ 카테고리 비활성화 시 FAQ 이동용
    List<Faq> findByUiCategoryId(UUID uiCategoryId);

    @Query("""
        SELECT f
        FROM Faq f
        WHERE f.isActive = true
          AND f.publishedAt = (
              SELECT MAX(f2.publishedAt)
              FROM Faq f2
              WHERE f2.domain = f.domain
                AND f2.isActive = true
          )
        """)
    List<Faq> findHomeFaqs();
}
