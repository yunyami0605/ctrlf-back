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
@Table(name = "rag_document", schema = "infra")
@Getter
@Setter
@NoArgsConstructor
public class RagDocument {

    /** RAG 문서 PK */
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    /** 문서 제목 */
    @Column(name = "title", length = 255)
    private String title;

    /** 문서 도메인(HR/보안/직무/개발 등) */
    @Column(name = "domain", length = 50)
    private String domain;

    /** 유저 UUID = 업로더 UUID(문자열) - 길이 36 (DB: varchar(36)) */
    @Column(name = "uploader_uuid", length = 36)
    private String uploaderUuid;

    /** 원본 파일 URL */
    @Column(name = "source_url", length = 255)
    private String sourceUrl;

    /** 등록 시각 */
    @Column(name = "created_at")
    private Instant createdAt;
}

