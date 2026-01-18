package com.ctrlf.education.repository;

import com.ctrlf.education.entity.Education;
import com.ctrlf.education.entity.EducationCategory;
import com.ctrlf.education.entity.EducationTopic;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * 교육 엔티티 기본 저장소
 */
public interface EducationRepository extends JpaRepository<Education, UUID> {

    /**
     * 삭제되지 않은 교육 조회
     */
    Optional<Education> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * 제목과 삭제 여부로 교육 조회
     */
    Optional<Education> findByTitleAndDeletedAtIsNull(String title);


    /**
     * 교육 목록 조회
     * - eduType 필터링
     * - 현재 활성 교육만 (deletedAt IS NULL, startAt~endAt 범위 내)
     * - Pageable로 정렬 및 페이지네이션 처리
     */
    @Query("SELECT e FROM Education e WHERE e.deletedAt IS NULL " +
           "AND (e.startAt IS NULL OR e.startAt <= CURRENT_TIMESTAMP) " +
           "AND (e.endAt IS NULL OR e.endAt >= CURRENT_TIMESTAMP) " +
           "AND (:eduType IS NULL OR e.eduType = :eduType)")
    List<Education> findEducations(
        @Param("eduType") EducationCategory eduType,
        Pageable pageable
    );

    /**
     * 사용자가 완료한 교육 ID 목록 조회 (completed 필터용).
     */
    @Query("SELECT ep.educationId FROM EducationProgress ep " +
           "WHERE ep.userUuid = :userUuid AND ep.isCompleted = true AND ep.deletedAt IS NULL")
    List<UUID> findCompletedEducationIdsByUser(@Param("userUuid") UUID userUuid);

    /**
     * 사용자가 진행 중인 교육 ID 목록 조회 (has_progress 확인용).
     */
    @Query("SELECT ep.educationId FROM EducationProgress ep " +
           "WHERE ep.userUuid = :userUuid AND ep.progress > 0 AND ep.deletedAt IS NULL")
    List<UUID> findInProgressEducationIdsByUser(@Param("userUuid") UUID userUuid);

    /**
     * 소프트 삭제: deleted_at을 현재 시각으로 설정.
     */
    @Transactional
    @Modifying
    @Query("UPDATE Education e SET e.deletedAt = CURRENT_TIMESTAMP, e.updatedAt = CURRENT_TIMESTAMP WHERE e.id = :id AND e.deletedAt IS NULL")
    int softDeleteById(@Param("id") UUID id);

    /**
     * 현재 활성화된 교육 조회 (삭제되지 않고, 현재 날짜가 startAt~endAt 사이인 교육).
     */
    @Query("SELECT e FROM Education e WHERE e.deletedAt IS NULL " +
           "AND (e.startAt IS NULL OR e.startAt <= CURRENT_TIMESTAMP) " +
           "AND (e.endAt IS NULL OR e.endAt >= CURRENT_TIMESTAMP)")
    List<Education> findAllActive();

    /**
     * 토픽(카테고리)별 교육 조회 (삭제되지 않은 것만).
     */
    @Query("SELECT e FROM Education e WHERE e.deletedAt IS NULL AND e.category = :topic")
    List<Education> findByTopic(@Param("topic") EducationTopic topic);

    /**
     * 토픽별 마감일 있는 교육 조회 (삭제되지 않은 것만).
     */
    @Query("SELECT e FROM Education e WHERE e.deletedAt IS NULL AND e.category = :topic AND e.endAt IS NOT NULL")
    List<Education> findByTopicWithDeadline(@Param("topic") EducationTopic topic);

    /**
     * 필수 활성 교육 조회 (삭제되지 않고, 시작일이 현재 이전인 것).
     */
    @Query("SELECT e FROM Education e WHERE e.deletedAt IS NULL AND e.require = true " +
           "AND (e.startAt IS NULL OR e.startAt <= :now)")
    List<Education> findActiveMandatory(@Param("now") Instant now);

    /**
     * 필수 교육 중 특정 기간 내 마감인 교육 조회.
     */
    @Query("SELECT e FROM Education e WHERE e.deletedAt IS NULL AND e.require = true " +
           "AND e.endAt IS NOT NULL AND e.endAt >= :startDate AND e.endAt < :endDate")
    List<Education> findMandatoryWithDeadlineBetween(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate);

    /**
     * 특정 기간 내 마감인 교육 조회 (삭제되지 않은 것만).
     */
    @Query("SELECT e FROM Education e WHERE e.deletedAt IS NULL " +
           "AND e.endAt IS NOT NULL AND e.endAt >= :startDate AND e.endAt < :endDate")
    List<Education> findWithDeadlineBetween(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate);
}
