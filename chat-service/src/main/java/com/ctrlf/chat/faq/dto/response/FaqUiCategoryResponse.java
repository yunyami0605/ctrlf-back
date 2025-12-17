package com.ctrlf.chat.faq.dto.response;

import com.ctrlf.chat.faq.entity.FaqUiCategory;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record FaqUiCategoryResponse(
    UUID id,
    String slug,
    String displayName,
    Integer sortOrder,
    Boolean isActive,
    Instant createdAt,
    Instant updatedAt
) {
    public static FaqUiCategoryResponse from(FaqUiCategory c) {
        return FaqUiCategoryResponse.builder()
            .id(c.getId())
            .slug(c.getSlug())
            .displayName(c.getDisplayName())
            .sortOrder(c.getSortOrder())
            .isActive(c.getIsActive())
            .createdAt(c.getCreatedAt())
            .updatedAt(c.getUpdatedAt())
            .build();
    }
}
