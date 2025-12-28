package com.ctrlf.education.script.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 교육 스크립트 엔티티.
 * 소스 문서로부터 생성된 스크립트 버전과 본문을 보관합니다.
 */
@Entity
@Table(name = "education_script", schema = "education")
@Getter
@Setter
@NoArgsConstructor
public class EducationScript {
    @Id
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    /** 대상 교육 ID */
    @Column(name = "education_id", columnDefinition = "uuid")
    private UUID educationId;

    /** 교육 영상 전체 제목 (LLM 생성) */
    @Column(name = "title")
    private String title;

    /** 전체 스크립트 기준 총 영상 길이(초) */
    @Column(name = "total_duration_sec")
    private Integer totalDurationSec;

    /** 스크립트 버전 */
    @Column(name = "version")
    private Integer version;

    /** 스크립트 생성에 사용된 LLM 모델 */
    @Column(name = "llm_model")
    private String llmModel;

    /** 스크립트 생성 프롬프트 해시값(재현성/비교용) */
    @Column(name = "generation_prompt_hash", length = 64)
    private String generationPromptHash;

    /** LLM 원본 응답 전체(JSON) – 감사/디버깅/회귀 테스트용 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb")
    private String rawPayload;

    /** 작성자 사용자 UUID */
    @Column(name = "created_by", columnDefinition = "uuid")
    private UUID createdBy;

    /** 생성 시각 */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    /** 삭제(소프트딜리트) 시각 */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    /** 수강 가능한 부서 목록(JSON) */
    @Column(name = "department_scope", columnDefinition = "text")
    private String departmentScope;

    /** 연결된 소스셋 ID (멀티문서 지원) */
    @Column(name = "source_set_id", columnDefinition = "uuid")
    private UUID sourceSetId;

    /** 스크립트 상태 (DRAFT, REVIEW_REQUESTED, APPROVED, REJECTED) */
    @Column(name = "status", length = 20)
    private String status;
}


