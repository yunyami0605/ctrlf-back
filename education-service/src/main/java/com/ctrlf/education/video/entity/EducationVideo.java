package com.ctrlf.education.video.entity;

import com.ctrlf.education.video.dto.VideoDtos.VideoStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 교육 영상(컨텐츠) 엔티티.
 * 교육 컨텐츠의 메타데이터와 생성된 영상 정보를 보관합니다.
 */
@Entity
@Table(name = "education_video", schema = "education")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EducationVideo {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    /** 대상 교육 ID */
    @Column(name = "education_id", columnDefinition = "uuid")
    private UUID educationId;

    /** 영상 제목 */
    @Column(name = "title")
    private String title;

    /** 생성 작업(Job) ID */
    @Column(name = "generation_job_id", columnDefinition = "uuid")
    private UUID generationJobId;

    /** 연결된 스크립트 ID */
    @Column(name = "script_id", columnDefinition = "uuid")
    private UUID scriptId;

    /** 레거시: 단일 문서일 때만 사용 (deprecated) */
    @Column(name = "material_id", columnDefinition = "uuid")
    private UUID materialId;

    /** 연결된 소스셋 ID (멀티문서 지원) */
    @Column(name = "source_set_id", columnDefinition = "uuid")
    private UUID sourceSetId;

    /** 영상 파일 URL */
    @Column(name = "file_url")
    private String fileUrl;

    /** 영상 버전 */
    @Column(name = "version")
    private Integer version;

    /** 영상 길이(초) */
    @Column(name = "duration")
    private Integer duration;

    /** 영상 상태 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private VideoStatus status;

    /** 생성 시각 */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    /** 최근 수정 시각 */
    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    /** 재생/표시 순서를 위한 인덱스 (교육 내 0-base) */
    @Column(name = "order_index")
    private Integer orderIndex;

    /** 삭제(소프트딜리트) 시각 */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    /** 제작자 UUID */
    @Column(name = "creator_uuid", columnDefinition = "uuid")
    private UUID creatorUuid;

    /**
     * 시드/유틸용 생성 팩토리.
     */
    public static EducationVideo create(
        UUID educationId,
        String title,
        String fileUrl,
        Integer duration,
        Integer version,
        VideoStatus status
    ) {
        EducationVideo v = new EducationVideo();
        v.setEducationId(educationId);
        v.setTitle(title);
        v.setFileUrl(fileUrl);
        v.setDuration(duration);
        v.setVersion(version);
        v.setStatus(status);
        v.setOrderIndex(0);
        return v;
    }

    /**
     * DRAFT 상태의 새 교육 컨텐츠 생성.
     * 영상 생성 전 초기 상태로 생성합니다.
     */
    public static EducationVideo createDraft(UUID educationId, String title, UUID creatorUuid) {
        EducationVideo v = new EducationVideo();
        v.setEducationId(educationId);
        v.setTitle(title);
        v.setStatus(VideoStatus.DRAFT);
        v.setVersion(1);
        v.setOrderIndex(0);
        v.setCreatorUuid(creatorUuid);
        return v;
    }
}


