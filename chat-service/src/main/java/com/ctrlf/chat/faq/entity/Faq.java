package com.ctrlf.chat.faq.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "faq", schema = "chat")
@Getter
@Setter
@NoArgsConstructor
public class Faq {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "question", columnDefinition = "text")
    private String question;

    @Column(name = "answer", columnDefinition = "text")
    private String answer;

    @Column(name = "domain")
    private String domain;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "priority")
    private Integer priority;

    // ✅ 추가: UI 카테고리 FK 역할 (실제 FK 제약은 MVP에선 생략 가능)
    @Column(name = "ui_category_id", columnDefinition = "uuid")
    private UUID uiCategoryId;

    // ✅ 추가: 카테고리 정리 필요 플래그
    @Column(name = "needs_recategorization")
    private Boolean needsRecategorization;

    // ✅ 추가: 게시 시각(대시보드/정렬)
    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
