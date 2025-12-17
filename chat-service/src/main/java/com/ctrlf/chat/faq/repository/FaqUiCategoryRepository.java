package com.ctrlf.chat.faq.repository;

import com.ctrlf.chat.faq.entity.FaqUiCategory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FaqUiCategoryRepository extends JpaRepository<FaqUiCategory, UUID> {

    Optional<FaqUiCategory> findBySlug(String slug);

    List<FaqUiCategory> findByIsActiveTrueOrderBySortOrderAsc();

    List<FaqUiCategory> findAllByOrderBySortOrderAsc();
}
