package com.ctrlf.education.entity;

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
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 교육 진행 현황 엔티티.
 * 사용자별 교육의 진행률/이수 여부 및 시청 정보(마지막 위치, 누적 시청 시간)를 보관합니다.
 */
@Entity
@Table(name = "education_progress", schema = "education")
@Getter
@Setter
@NoArgsConstructor
public class EducationProgress {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    /** 진행 주체 사용자 UUID */
    @Column(name = "user_uuid", columnDefinition = "uuid")
    private UUID userUuid;

    /** 대상 교육 ID */
    @Column(name = "education_id", columnDefinition = "uuid")
    private UUID educationId;

    /** 진행률(%) */
    @Column(name = "progress")
    private Integer progress;

    /** 이수 여부 */
    @Column(name = "is_completed")
    private Boolean isCompleted;

    /** 이수 완료 시각 */
    @Column(name = "completed_at")
    private Instant completedAt;

    /** 최근 진행 업데이트 시각 */
    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    /** 마지막 재생 위치(초) */
    @Column(name = "last_position_seconds")
    private Integer lastPositionSeconds;

    /** 누적 시청 시간(초) */
    @Column(name = "total_watch_seconds")
    private Integer totalWatchSeconds;

    /** 삭제(소프트딜리트) 시각 */
    @Column(name = "deleted_at")
    private Instant deletedAt;
}


