package com.ctrlf.education.video.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 소스셋(SourceSet) 엔티티.
 * 여러 문서를 묶어 "스크립트 1개/영상 1개"를 만드는 제작 단위.
 */
@Entity
@Table(name = "source_set", schema = "education")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SourceSet {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    /** 소스셋 제목 */
    @Column(name = "title", length = 255, nullable = false)
    private String title;

    /** 연결된 교육 ID (선택적) */
    @Column(name = "education_id", columnDefinition = "uuid")
    private UUID educationId;

    /** 연결된 영상 ID (선택적) */
    @Column(name = "video_id", columnDefinition = "uuid")
    private UUID videoId;

    /** 소스셋 도메인 */
    @Column(name = "domain", length = 50)
    private String domain;

    /** 상태: CREATED, LOCKED, SCRIPT_READY, FAILED */
    @Column(name = "status", length = 20, nullable = false)
    private String status;

    /** 요청자 UUID */
    @Column(name = "requested_by", length = 36)
    private String requestedBy;

    /** 생성 시각 */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 수정 시각 */
    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    /** 삭제 시각 (soft delete) */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    /** 에러 코드 (실패 시) */
    @Column(name = "error_code", length = 50)
    private String errorCode;

    /** 실패 사유 (실패 시) */
    @Column(name = "fail_reason", length = 1000)
    private String failReason;

    /** 소스셋에 포함된 문서 목록 */
    @OneToMany(mappedBy = "sourceSet")
    private List<SourceSetDocument> documents = new ArrayList<>();

    /**
     * 소스셋 생성 팩토리 메서드.
     */
    public static SourceSet create(String title, String domain, String requestedBy, UUID educationId, UUID videoId) {
        SourceSet ss = new SourceSet();
        ss.setTitle(title);
        ss.setDomain(domain);
        ss.setStatus("CREATED");
        ss.setRequestedBy(requestedBy);
        ss.setEducationId(educationId);
        ss.setVideoId(videoId);
        return ss;
    }
}
