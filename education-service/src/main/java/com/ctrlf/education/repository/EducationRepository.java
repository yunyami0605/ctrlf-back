package com.ctrlf.education.repository;

import com.ctrlf.education.entity.Education;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface EducationRepository extends JpaRepository<Education, UUID> {

    @Query(
        value = """
            SELECT 
              e.id,
              e.title,
              e.require AS required,
              CASE 
                WHEN :userUuid IS NOT NULL THEN COALESCE(
                  (SELECT ep.is_completed 
                     FROM education.education_progress ep 
                    WHERE ep.education_id = e.id 
                      AND ep.user_uuid = CAST(:userUuid AS uuid)
                  ), FALSE)
                ELSE FALSE
              END AS is_completed
            FROM education.education e
            WHERE e.deleted_at IS NULL
              AND (:year IS NULL OR EXTRACT(YEAR FROM e.created_at) = :year)
              AND (:category IS NULL OR e.category = :category)
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
            ORDER BY e.created_at DESC NULLS LAST
            LIMIT :size OFFSET :offset
        """,
        nativeQuery = true
    )
    List<Object[]> findEducationsNative(
        @Param("offset") int offset,
        @Param("size") int size,
        @Param("completed") Boolean completed,
        @Param("year") Integer year,
        @Param("category") String category,
        @Param("userUuid") UUID userUuid
    );
}
