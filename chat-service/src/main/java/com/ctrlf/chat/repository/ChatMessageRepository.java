package com.ctrlf.chat.repository;

import com.ctrlf.chat.entity.ChatMessage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    List<ChatMessage> findAllBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    Optional<ChatMessage> findTopBySessionIdOrderByCreatedAtDesc(UUID sessionId);

    // ✅ 추가: 해당 세션에서 가장 최근 user 메시지 1개
    Optional<ChatMessage> findTopBySessionIdAndRoleOrderByCreatedAtDesc(UUID sessionId, String role);

    @Query(
        value = """
            SELECT *
            FROM chat.chat_message m
            WHERE m.session_id = :sessionId
              AND (
                :cursorCreatedAt IS NULL
                OR m.created_at < :cursorCreatedAt
                OR (m.created_at = :cursorCreatedAt AND m.id < :cursorId)
              )
            ORDER BY m.created_at DESC, m.id DESC
            LIMIT :limit
            """,
        nativeQuery = true
    )
    List<ChatMessage> findNextPageBySessionId(
        @Param("sessionId") UUID sessionId,
        @Param("cursorCreatedAt") Instant cursorCreatedAt,
        @Param("cursorId") UUID cursorId,
        @Param("limit") int limit
    );
}
