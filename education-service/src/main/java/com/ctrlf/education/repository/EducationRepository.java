package com.ctrlf.education.repository;

import com.ctrlf.education.entity.Education;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * 교육 엔티티 기본 저장소.
 * 목록 조회를 위한 네이티브 쿼리를 포함합니다.
 */
public interface EducationRepository extends JpaRepository<Education, UUID> {


    /**
     * 교육 목록(간략+추가 정보) 조회 V2.
     * - title, description, category, require, is_completed, updated_at 포함
     * - sort: UPDATED(기본) 또는 TITLE
     */
    @Query(
        value = """
            SELECT 
              e.id,
              e.title,
              e.description,
              e.category,
              e.require AS required,
              CASE 
                WHEN :userUuid IS NOT NULL THEN COALESCE(
                  (SELECT ep.is_completed 
                     FROM education.education_progress ep 
                    WHERE ep.education_id = e.id 
                      AND ep.user_uuid = CAST(:userUuid AS uuid)
                  ), FALSE)
                ELSE FALSE
              END AS is_completed,
              CASE
                WHEN :userUuid IS NOT NULL THEN EXISTS(
                   SELECT 1
                     FROM education.education_progress ep2
                    WHERE ep2.education_id = e.id
                      AND ep2.user_uuid = CAST(:userUuid AS uuid)
                      AND COALESCE(ep2.progress, 0) > 0
                )
                ELSE FALSE
              END AS has_progress,
              e.updated_at
            FROM education.education e
            WHERE e.deleted_at IS NULL
              AND (:year IS NULL OR EXTRACT(YEAR FROM e.created_at) = :year)
              AND (:eduType IS NULL OR e.edu_type = :eduType)
              AND (
                    :completed IS NULL 
                 OR (:userUuid IS NOT NULL AND 
                     COALESCE(
                       (SELECT ep.is_completed 
                          FROM education.education_progress ep 
                         WHERE ep.education_id = e.id 
                           AND ep.user_uuid = CAST(:userUuid AS uuid)
                       ), FALSE) = :completed
                   )
              )
            ORDER BY 
              CASE WHEN :sort = 'TITLE' THEN e.title END ASC NULLS LAST,
              CASE WHEN :sort <> 'TITLE' THEN e.updated_at END DESC NULLS LAST
            LIMIT :size OFFSET :offset
        """,
        nativeQuery = true
    )
    List<Object[]> findEducationsNative(
        @Param("offset") int offset,
        @Param("size") int size,
        @Param("completed") Boolean completed,
        @Param("year") Integer year,
        @Param("eduType") String eduType,
        @Param("userUuid") UUID userUuid,
        @Param("sort") String sort
    );

    /**
     * 소프트 삭제: deleted_at을 현재 시각으로 설정.
     */
    @Transactional
    @Modifying
    @Query(value = "UPDATE education.education SET deleted_at = now(), updated_at = now() WHERE id = :id AND deleted_at IS NULL", nativeQuery = true)
    int softDeleteById(@Param("id") UUID id);
}
