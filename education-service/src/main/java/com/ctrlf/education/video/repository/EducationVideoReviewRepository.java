package com.ctrlf.education.video.repository;

import com.ctrlf.education.video.entity.EducationVideoReview;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 교육 영상 검수(리뷰) 저장소.
 */
public interface EducationVideoReviewRepository extends JpaRepository<EducationVideoReview, UUID> {
    /**
     * 특정 영상의 감사 이력 조회 (삭제되지 않은 것만, 최신순).
     */
    @Query("SELECT r FROM EducationVideoReview r WHERE r.videoId = :videoId AND r.deletedAt IS NULL ORDER BY r.createdAt DESC")
    List<EducationVideoReview> findByVideoIdOrderByCreatedAtDesc(@Param("videoId") UUID videoId);
}


