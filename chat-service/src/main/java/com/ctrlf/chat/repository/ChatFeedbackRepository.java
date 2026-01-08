package com.ctrlf.chat.repository;

import com.ctrlf.chat.entity.ChatFeedback;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatFeedbackRepository extends JpaRepository<ChatFeedback, UUID> {

    /**
     * 메시지ID + 사용자UUID로 기존 피드백 조회
     */
    Optional<ChatFeedback> findByMessageIdAndUserUuid(UUID messageId, UUID userUuid);

    /**
     * 원자적 UPSERT (PostgreSQL ON CONFLICT)
     * - 동시성 문제 없이 INSERT 또는 UPDATE 수행
     * - 신규: INSERT, 기존: session_id/score/comment/updated_at UPDATE
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = """
            INSERT INTO chat.chat_feedback (id, session_id, message_id, user_uuid, score, comment, created_at, updated_at)
            VALUES (gen_random_uuid(), :sessionId, :messageId, :userUuid, :score, :comment, NOW(), NOW())
            ON CONFLICT (message_id, user_uuid)
            DO UPDATE SET
                session_id = EXCLUDED.session_id,
                score = EXCLUDED.score,
                comment = EXCLUDED.comment,
                updated_at = NOW()
            """,
        nativeQuery = true
    )
    int upsertFeedback(
        @Param("sessionId") UUID sessionId,
        @Param("messageId") UUID messageId,
        @Param("userUuid") UUID userUuid,
        @Param("score") Integer score,
        @Param("comment") String comment
    );

    /**
     * 응답 만족도 조회 (%)
     * 평점 4점 이상을 만족으로 간주
     */
    @Query(
        value = """
            SELECT CASE
                WHEN COUNT(*) = 0 THEN 0.0
                ELSE (COUNT(CASE WHEN f.score >= 4 THEN 1 END) * 100.0 / COUNT(*))
            END
            FROM chat.chat_feedback f
            INNER JOIN chat.chat_message m ON m.id = f.message_id
            WHERE f.created_at >= :startDate
              AND (:department IS NULL OR m.department = :department)
            """,
        nativeQuery = true
    )
    Double getSatisfactionRate(
        @Param("startDate") Instant startDate,
        @Param("department") String department
    );
}
