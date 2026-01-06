package com.ctrlf.infra.ailog.repository;

import com.ctrlf.infra.ailog.entity.AiLog;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * AI 로그 Repository
 */
@Repository
public interface AiLogRepository extends JpaRepository<AiLog, UUID>, JpaSpecificationExecutor<AiLog> {
}

