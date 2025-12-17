package com.ctrlf.chat.faq.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * FAQ 화면용 UI 카테고리 엔티티
 */
@Entity
@Table(name = "faq_ui_categories", schema = "chat")
@Getter
@Setter
@NoArgsConstructor
public class FaqUiCategory {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String slug;

    private String displayName;
    private Integer sortOrder;
    private Boolean isActive;

    private UUID createdBy;
    private UUID updatedBy;

    private Instant createdAt;
    private Instant updatedAt;

    public static FaqUiCategory create(
        UUID id,
        String slug,
        String displayName,
        int sortOrder,
        UUID operatorId
    ) {
        FaqUiCategory c = new FaqUiCategory();
        c.id = id;
        c.slug = slug;
        c.displayName = displayName;
        c.sortOrder = sortOrder;
        c.isActive = true;
        c.createdBy = operatorId;
        c.updatedBy = operatorId;
        c.createdAt = Instant.now();
        c.updatedAt = Instant.now();
        return c;
    }

    public void update(
        String displayName,
        Integer sortOrder,
        Boolean isActive,
        UUID operatorId
    ) {
        if (displayName != null) this.displayName = displayName;
        if (sortOrder != null) this.sortOrder = sortOrder;
        if (isActive != null) this.isActive = isActive;
        this.updatedBy = operatorId;
        this.updatedAt = Instant.now();
    }
}
