package com.ctrlf.chat.faq.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "faq_candidate", schema = "chat")
@Getter
@Setter
@NoArgsConstructor
public class FaqCandidate {

    /** FAQ 후보 PK */
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    /** 대표 질문(canonical question) */
    @Column(name = "canonical_question", columnDefinition = "text", nullable = false)
    private String canonicalQuestion;

    /** 도메인 */
    @Column(name = "domain", nullable = false)
    private String domain;

    /** 최근 7일 질문 수 */
    @Column(name = "question_count_7d")
    private Integer questionCount7d;

    /** 최근 30일 질문 수 */
    @Column(name = "question_count_30d")
    private Integer questionCount30d;

    /** 의도 신뢰도 평균 */
    @Column(name = "avg_intent_confidence")
    private Double avgIntentConfidence;

    /** PII 감지 여부 */
    @Column(name = "pii_detected", nullable = false)
    private Boolean piiDetected = false;

    /** 후보 점수 */
    @Column(name = "score_candidate")
    private Double scoreCandidate;

    /** 후보 상태 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CandidateStatus status = CandidateStatus.NEW;

    /** 마지막 질문 시각 */
    @Column(name = "last_asked_at")
    private Instant lastAskedAt;

    /** 생성 시각 */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public enum CandidateStatus {
        NEW,
        ELIGIBLE,
        EXCLUDED
    }
}
