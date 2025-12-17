package com.ctrlf.chat.faq.dto.response;

import com.ctrlf.chat.faq.entity.FaqCandidate;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

/**
 * FAQ 후보 응답 DTO.
 */
@Getter
@Builder
public class FaqCandidateResponse {

    /** 후보 ID */
    private UUID id;

    /** 대표 질문 (canonical question) */
    private String canonicalQuestion;

    /** 도메인 */
    private String domain;

    /** 최근 7일 질문 수 */
    private Integer questionCount7d;

    /** 최근 30일 질문 수 */
    private Integer questionCount30d;

    /** 평균 의도 신뢰도 */
    private Double avgIntentConfidence;

    /** PII 감지 여부 */
    private Boolean piiDetected;

    /** 후보 점수 */
    private Double scoreCandidate;

    /** 후보 상태 */
    private FaqCandidate.CandidateStatus status;

    /** 마지막 질문 시각 */
    private Instant lastAskedAt;

    /** 생성 시각 */
    private Instant createdAt;

    public static FaqCandidateResponse from(FaqCandidate c) {
        return FaqCandidateResponse.builder()
            .id(c.getId())
            .canonicalQuestion(c.getCanonicalQuestion())
            .domain(c.getDomain())
            .questionCount7d(c.getQuestionCount7d())
            .questionCount30d(c.getQuestionCount30d())
            .avgIntentConfidence(c.getAvgIntentConfidence())
            .piiDetected(c.getPiiDetected())
            .scoreCandidate(c.getScoreCandidate())
            .status(c.getStatus())
            .lastAskedAt(c.getLastAskedAt())
            .createdAt(c.getCreatedAt())
            .build();
    }
}
