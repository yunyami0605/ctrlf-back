package com.ctrlf.infra.rag.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "rag_fail_chunk", schema = "infra")
@Getter
@Setter
@NoArgsConstructor
public class RagFailChunk {

    /** 임베딩 실패 로그 PK */
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    /** 문서 ID */
    @Column(name = "document_id", columnDefinition = "uuid")
    private UUID documentId;

    /** 실패한 청크 인덱스 */
    @Column(name = "chunk_index")
    private Integer chunkIndex;

    /** 실패 사유 */
    @Column(name = "fail_reason", columnDefinition = "text")
    private String failReason;

    /** 기록 시각 */
    @Column(name = "created_at")
    private Instant createdAt;
}

