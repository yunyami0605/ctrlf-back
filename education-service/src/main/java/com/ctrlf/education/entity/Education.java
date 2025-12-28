package com.ctrlf.education.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 교육 메타 정보 엔티티.
 * 카테고리/필수 여부/통과 기준 등 기본 속성을 보관합니다.
 */
@Entity
@Table(name = "education", schema = "education")
@Getter
@Setter
@NoArgsConstructor
public class Education {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "title")
    private String title;

    /** 카테고리(예: 직무/성희롱 예방/개인 정보 보호/직장 내 괴롭힘/장애인 인식 개선) */
    @Enumerated(EnumType.STRING)
    @Column(name = "category")
    private EducationTopic category;

    /** 유형(예: MANDATORY, JOB, ETC) */
    @Enumerated(EnumType.STRING)
    @Column(name = "edu_type")
    private EducationCategory eduType;

    @Column(name = "description")
    private String description;

    /** 통과 기준 점수 */
    @Column(name = "pass_score")
    private Integer passScore;

    /** 시청률 통과 기준 비율 */
    @Column(name = "pass_ratio")
    private Integer passRatio;

    /** 필수 교육 여부 */
    @Column(name = "require")
    private Boolean require;

    /** 생성 시각 */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    /** 최근 수정 시각 */
    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    /** 삭제(소프트딜리트) 시각 */
    @Column(name = "deleted_at")
    private Instant deletedAt;
}


