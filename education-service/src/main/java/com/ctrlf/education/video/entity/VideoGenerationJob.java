package com.ctrlf.education.video.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 영상 생성 작업(Job) 엔티티.
 * 템플릿 옵션/상태/실패 사유/결과물 URL 등을 보관합니다.
 */
@Entity
@Table(name = "video_generation_job", schema = "education")
@Getter
@Setter
@NoArgsConstructor
public class VideoGenerationJob {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    /** 대상 교육 ID */
    @Column(name = "education_id", columnDefinition = "uuid")
    private UUID educationId;

    /** 입력 스크립트 ID */
    @Column(name = "script_id", columnDefinition = "uuid")
    private UUID scriptId;

    /** 생성 템플릿 옵션(JSON) */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "template_option", columnDefinition = "json")
    private String templateOption;

    /** 상태(예: PENDING/PROCESSING/SUCCEEDED/FAILED 등) */
    @Column(name = "status")
    private String status;

    /** 실패 사유(실패 시) */
    @Column(name = "fail_reason")
    private String failReason;

    /** 생성된 결과 영상 URL(성공 시) */
    @Column(name = "generated_video_url")
    private String generatedVideoUrl;

    /** 영상 길이(초) */
    @Column(name = "duration")
    private Integer duration;

    /** 재시도 횟수 */
    @Column(name = "retry_count")
    private Integer retryCount;

    /** 생성 시각 */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    /** 최근 상태 갱신 시각 */
    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    /** 삭제(소프트딜리트) 시각 */
    @Column(name = "deleted_at")
    private Instant deletedAt;
}


