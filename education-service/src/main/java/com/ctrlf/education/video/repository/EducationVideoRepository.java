package com.ctrlf.education.video.repository;

import com.ctrlf.education.video.entity.EducationVideo;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * 교육 영상 저장소.
 */
public interface EducationVideoRepository extends JpaRepository<EducationVideo, UUID> {
    /**
     * 특정 교육에 속한 영상 목록 조회.
     */
    List<EducationVideo> findByEducationId(UUID educationId);
    List<EducationVideo> findByEducationIdOrderByOrderIndexAscCreatedAtAsc(UUID educationId);

    /**
     * 생성 Job ID로 영상 조회.
     */
    Optional<EducationVideo> findByGenerationJobId(UUID generationJobId);
    /**
     * 특정 교육의 모든 영상을 삭제.
     */
    void deleteByEducationId(UUID educationId);

    /**
     * 특정 교육의 모든 영상 소프트 삭제.
     */
    @Transactional
    @Modifying
    @Query(value = "UPDATE education.education_video SET deleted_at = now() WHERE education_id = :educationId AND deleted_at IS NULL", nativeQuery = true)
    int softDeleteByEducationId(@Param("educationId") UUID educationId);

    /**
     * 스크립트 ID로 영상 조회 (삭제되지 않은 것만).
     */
    @Query("SELECT v FROM EducationVideo v WHERE v.scriptId = :scriptId AND v.deletedAt IS NULL")
    List<EducationVideo> findByScriptId(@Param("scriptId") UUID scriptId);

    /**
     * 특정 교육에 속한 영상 목록 조회 (status 필터).
     */
    List<EducationVideo> findByEducationIdAndStatusOrderByOrderIndexAscCreatedAtAsc(
        UUID educationId, String status);

    /**
     * 검토 대기 영상 조회 (SCRIPT_REVIEW_REQUESTED 또는 FINAL_REVIEW_REQUESTED).
     */
    @Query("SELECT v FROM EducationVideo v WHERE v.status IN ('SCRIPT_REVIEW_REQUESTED', 'FINAL_REVIEW_REQUESTED') AND v.deletedAt IS NULL")
    List<EducationVideo> findPendingReviewVideos();

    /**
     * 승인된 영상 개수 (PUBLISHED 상태).
     */
    @Query("SELECT COUNT(v) FROM EducationVideo v WHERE v.status = 'PUBLISHED' AND v.deletedAt IS NULL")
    Long countApprovedVideos();

    /**
     * 반려된 영상 개수 (EducationVideoReview가 있는 영상).
     */
    @Query("SELECT COUNT(DISTINCT r.videoId) FROM EducationVideoReview r WHERE r.deletedAt IS NULL")
    Long countRejectedVideos();

    /**
     * 특정 검토자가 처리한 영상 개수.
     */
    @Query("SELECT COUNT(DISTINCT r.videoId) FROM EducationVideoReview r WHERE r.reviewerUuid = :reviewerUuid AND r.deletedAt IS NULL")
    Long countMyActivityVideos(@Param("reviewerUuid") UUID reviewerUuid);

    /**
     * 승인된 영상 조회 (PUBLISHED 상태).
     */
    @Query("SELECT v FROM EducationVideo v WHERE v.status = 'PUBLISHED' AND v.deletedAt IS NULL")
    List<EducationVideo> findApprovedVideos();

    /**
     * 반려된 영상 조회 (EducationVideoReview가 있는 영상).
     */
    @Query("SELECT DISTINCT v FROM EducationVideo v " +
           "INNER JOIN EducationVideoReview r ON v.id = r.videoId " +
           "WHERE r.deletedAt IS NULL AND v.deletedAt IS NULL")
    List<EducationVideo> findRejectedVideos();
}


