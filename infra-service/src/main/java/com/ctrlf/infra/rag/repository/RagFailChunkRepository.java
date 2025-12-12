package com.ctrlf.infra.rag.repository;

import com.ctrlf.infra.rag.entity.RagFailChunk;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface RagFailChunkRepository extends JpaRepository<RagFailChunk, UUID> {

    @Transactional
    void deleteByDocumentId(UUID documentId);
}

