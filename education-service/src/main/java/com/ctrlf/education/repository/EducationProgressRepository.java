package com.ctrlf.education.repository;

import com.ctrlf.education.entity.EducationProgress;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 교육 진행 현황 저장소.
 */
public interface EducationProgressRepository extends JpaRepository<EducationProgress, UUID> {
    /** 사용자별 이수 완료한 교육 진행 현황 조회 */
    List<EducationProgress> findByUserUuidAndIsCompletedTrue(UUID userUuid);
    
    /** 사용자별 특정 교육의 진행 현황 조회 */
    Optional<EducationProgress> findByUserUuidAndEducationId(UUID userUuid, UUID educationId);

    /** 삭제되지 않은 모든 진행 기록 조회 */
    @Query("SELECT p FROM EducationProgress p WHERE p.deletedAt IS NULL")
    List<EducationProgress> findAllNotDeleted();

    /** 기간 내에 완료된 진행 기록 조회 */
    @Query("SELECT p FROM EducationProgress p WHERE p.deletedAt IS NULL AND p.completedAt IS NOT NULL AND p.completedAt > :startDate")
    List<EducationProgress> findCompletedAfter(@Param("startDate") Instant startDate);

    /** 사용자의 여러 교육에 대한 진행 정보 한 번에 조회 */
    @Query("SELECT p FROM EducationProgress p WHERE p.userUuid = :userUuid AND p.educationId IN :educationIds AND p.deletedAt IS NULL")
    List<EducationProgress> findByUserUuidAndEducationIdIn(@Param("userUuid") UUID userUuid, @Param("educationIds") Collection<UUID> educationIds);
}


