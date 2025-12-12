package com.ctrlf.infra.rag.repository;

import com.ctrlf.infra.rag.entity.RagDocumentChunk;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface RagDocumentChunkRepository extends JpaRepository<RagDocumentChunk, UUID> {

    @Transactional
    void deleteByDocumentId(UUID documentId);
}

