package com.ctrlf.chat.faq.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * AI가 생성한 FAQ 초안(Draft) 엔티티
 */
@Entity
@Table(name = "faq_drafts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class FaqDraft {

    @Id
    @GeneratedValue
    private UUID id;

    private String faqDraftId;
    private String domain;
    private String clusterId;

    private String question;

    @Column(columnDefinition = "TEXT")
    private String answerMarkdown;

    @Column(columnDefinition = "TEXT")
    private String summary;

    private Double aiConfidence;

    @Enumerated(EnumType.STRING)
    private Status status;

    private UUID reviewerId;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.status = Status.DRAFT;
    }

    public void publish(UUID reviewerId) {
        this.status = Status.PUBLISHED;
        this.reviewerId = reviewerId;
        this.publishedAt = LocalDateTime.now();
    }

    public void reject(UUID reviewerId) {
        this.status = Status.REJECTED;
        this.reviewerId = reviewerId;
    }

    public enum Status {
        DRAFT,
        PUBLISHED,
        REJECTED
    }
}
