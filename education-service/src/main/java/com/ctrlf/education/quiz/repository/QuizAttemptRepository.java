package com.ctrlf.education.quiz.repository;

import com.ctrlf.education.quiz.entity.QuizAttempt;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, UUID> {
    Optional<QuizAttempt> findTopByUserUuidAndEducationIdAndSubmittedAtIsNullOrderByCreatedAtDesc(UUID userUuid, UUID educationId);
    long countByUserUuidAndEducationId(UUID userUuid, UUID educationId);
    
    /** 사용자별 제출 완료된 퀴즈 시도 목록 조회 */
    List<QuizAttempt> findByUserUuidAndSubmittedAtIsNotNullOrderByCreatedAtDesc(UUID userUuid);
    
    /** 교육별 사용자의 제출 완료된 퀴즈 시도 목록 조회 (재응시 시 이전 문항 제외용) */
    List<QuizAttempt> findByUserUuidAndEducationIdAndSubmittedAtIsNotNullOrderByCreatedAtDesc(UUID userUuid, UUID educationId);
    
    /** 교육별 사용자의 최고 점수 시도 조회 */
    @Query("SELECT a FROM QuizAttempt a WHERE a.userUuid = :userUuid AND a.educationId = :educationId AND a.submittedAt IS NOT NULL ORDER BY COALESCE(a.score, 0) DESC, a.createdAt DESC")
    List<QuizAttempt> findTopByUserUuidAndEducationIdOrderByScoreDesc(@Param("userUuid") UUID userUuid, @Param("educationId") UUID educationId);
    
    /** 교육별 모든 제출 완료된 퀴즈 시도 조회 (부서별 통계용) */
    List<QuizAttempt> findByEducationIdAndSubmittedAtIsNotNull(UUID educationId);
    
    /** 사용자별 교육의 삭제되지 않은 퀴즈 시도 목록 조회 */
    List<QuizAttempt> findByUserUuidAndEducationIdAndDeletedAtIsNull(UUID userUuid, UUID educationId);
    
    /**
     * 제출 완료된 퀴즈 시도 조회 (기간/부서 필터).
     * - submittedAt이 startDate 이후인 것만
     * - deletedAt이 null인 것만
     * - department 필터 (선택적)
     */
    @Query("SELECT a FROM QuizAttempt a WHERE a.submittedAt IS NOT NULL " +
           "AND a.submittedAt >= :startDate AND a.deletedAt IS NULL " +
           "AND (:department IS NULL OR :department = '' OR a.department = :department)")
    List<QuizAttempt> findSubmittedAfterDate(
        @Param("startDate") Instant startDate,
        @Param("department") String department
    );
    
    /**
     * 제출 완료된 퀴즈 시도 조회 (기간 필터, 부서가 있는 것만).
     * - submittedAt이 startDate 이후인 것만
     * - deletedAt이 null인 것만
     * - department가 null이 아니고 비어있지 않은 것만
     * - department 필터 (선택적)
     */
    @Query("SELECT a FROM QuizAttempt a WHERE a.submittedAt IS NOT NULL " +
           "AND a.submittedAt >= :startDate AND a.deletedAt IS NULL " +
           "AND a.department IS NOT NULL AND a.department != '' " +
           "AND (:department IS NULL OR :department = '' OR a.department = :department)")
    List<QuizAttempt> findSubmittedAfterDateWithDepartment(
        @Param("startDate") Instant startDate,
        @Param("department") String department
    );
    
    /**
     * 사용자별 특정 교육 목록의 제출 완료된 퀴즈 시도 조회.
     * - 특정 교육 ID 목록에 해당하는 것만 조회
     * - submittedAt이 null이 아닌 것만
     */
    @Query("SELECT a FROM QuizAttempt a WHERE a.userUuid = :userUuid " +
           "AND a.educationId IN :educationIds AND a.submittedAt IS NOT NULL " +
           "ORDER BY a.createdAt DESC")
    List<QuizAttempt> findByUserUuidAndEducationIdInAndSubmittedAtIsNotNullOrderByCreatedAtDesc(
        @Param("userUuid") UUID userUuid,
        @Param("educationIds") java.util.Collection<UUID> educationIds
    );
}


