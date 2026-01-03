package com.ctrlf.education.video.repository;

import com.ctrlf.education.video.entity.EducationVideoProgress;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * 교육 영상 진행률 저장소.
 */
public interface EducationVideoProgressRepository extends JpaRepository<EducationVideoProgress, UUID> {
    /**
     * 사용자/교육/영상 기준의 단일 진행률을 조회합니다.
     */
    Optional<EducationVideoProgress> findByUserUuidAndEducationIdAndVideoId(UUID userUuid, UUID educationId, UUID videoId);
    /**
     * 사용자/교육 기준의 전체 영상 진행률 목록을 조회합니다.
     */
    List<EducationVideoProgress> findByUserUuidAndEducationId(UUID userUuid, UUID educationId);
    /**
     * 특정 교육의 모든 진행 이력을 삭제합니다.
     */
    void deleteByEducationId(UUID educationId);

    /**
     * 특정 교육의 모든 진행 이력을 소프트 삭제합니다.
     */
    @Transactional
    @Modifying
    @Query(value = "UPDATE education.education_video_progress SET deleted_at = now() WHERE education_id = :educationId AND deleted_at IS NULL", nativeQuery = true)
    int softDeleteByEducationId(@Param("educationId") UUID educationId);

    /**
     * 특정 비디오의 모든 진행 이력을 삭제합니다.
     */
    void deleteByVideoId(UUID videoId);
}


