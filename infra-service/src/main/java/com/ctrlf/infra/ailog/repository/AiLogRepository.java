package com.ctrlf.infra.ailog.repository;

import com.ctrlf.infra.ailog.entity.AiLog;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * AI 로그 Repository
 */
@Repository
public interface AiLogRepository extends JpaRepository<AiLog, UUID>, JpaSpecificationExecutor<AiLog> {

    /**
     * traceId, conversationId, turnId 조합으로 중복 체크
     * 
     * @param traceId 트레이스 ID
     * @param conversationId 대화 ID
     * @param turnId 턴 ID
     * @return 존재하는 로그 (있으면 중복)
     */
    @Query("""
        SELECT a
        FROM AiLog a
        WHERE a.traceId = :traceId
          AND a.conversationId = :conversationId
          AND a.turnId = :turnId
        """)
    Optional<AiLog> findByTraceIdAndConversationIdAndTurnId(
        @Param("traceId") String traceId,
        @Param("conversationId") String conversationId,
        @Param("turnId") Integer turnId
    );

}

