package com.ctrlf.infra.rag.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
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
    @Column(name = "title", length = 255, columnDefinition = "varchar(255)")
    private String title;

    /** 문서 도메인(HR/보안/직무/개발 등) */
    @Column(name = "domain", length = 50, columnDefinition = "varchar(50)")
    private String domain;

    /** 사규 문서 ID (예: POL-EDU-015) - 사규 관리용 (같은 documentId에 여러 버전이 있을 수 있음) */
    @Column(name = "document_id", length = 50, columnDefinition = "varchar(50)")
    private String documentId;

    /** 문서 버전 (사규 관리용) */
    @Column(name = "version")
    private Integer version;

    /** 변경 요약 (사규 관리용) */
    @Column(name = "change_summary", columnDefinition = "text")
    private String changeSummary;

    /** 유저 UUID = 업로더 UUID(문자열) - 길이 36 (DB: varchar(36)) */
    @Column(name = "uploader_uuid", length = 36, columnDefinition = "varchar(36)")
    private String uploaderUuid;

    /** 원본 파일 URL */
    @Column(name = "source_url", length = 255, columnDefinition = "varchar(255)")
    private String sourceUrl;

    /** 처리 상태 (RAG 문서 처리 상태 또는 사규 관리 상태) */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, columnDefinition = "varchar(20)")
    private RagDocumentStatus status;

    /** 등록 시각 */
    @Column(name = "created_at")
    private Instant createdAt;

    /** 처리 완료 시각 */
    @Column(name = "processed_at")
    private Instant processedAt;

    /** 전처리 상태 (IDLE, PROCESSING, READY, FAILED), REACDY 면 검토 가능한 상태*/
    @Column(name = "preprocess_status", length = 20, columnDefinition = "varchar(20) DEFAULT 'IDLE'")
    private String preprocessStatus;

    /** 전처리 실패 사유 */
    @Column(name = "preprocess_error", columnDefinition = "text")
    private String preprocessError;

    /** 검토 요청 시각 */
    @Column(name = "review_requested_at")
    private Instant reviewRequestedAt;

    /** 검토 항목 ID */
    @Column(name = "review_item_id", length = 100, columnDefinition = "varchar(100)")
    private String reviewItemId;

    /** 반려 사유 */
    @Column(name = "reject_reason", columnDefinition = "text")
    private String rejectReason;

    /** 반려 시각 */
    @Column(name = "rejected_at")
    private Instant rejectedAt;
}

