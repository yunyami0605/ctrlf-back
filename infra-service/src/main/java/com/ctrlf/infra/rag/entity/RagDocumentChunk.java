package com.ctrlf.infra.rag.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "rag_document_chunk", schema = "infra")
@Getter
@Setter
@NoArgsConstructor
public class RagDocumentChunk {

    /** 문서 청크 PK */
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    /** 원본 문서 ID */
    @Column(name = "document_id", columnDefinition = "uuid")
    private UUID documentId;

    /** 청크 번호(0..n) */
    @Column(name = "chunk_index")
    private Integer chunkIndex;

    /** 텍스트 청크 내용 */
    @Column(name = "chunk_text", columnDefinition = "text")
    private String chunkText;

    /** 임베딩 벡터(pgvector) */
    @JdbcTypeCode(Types.OTHER)
    @Column(name = "embedding", columnDefinition = "vector(1536)")
    private String embedding;

    /** 임베딩 생성 시각 */
    @Column(name = "created_at")
    private Instant createdAt;
}

