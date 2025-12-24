package com.ctrlf.education.video.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * 소스셋과 문서의 관계 엔티티 (다대다).
 */
@Entity
@Table(name = "source_set_document", schema = "education")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SourceSetDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    /** 소스셋 ID */
    @ManyToOne
    @JoinColumn(name = "source_set_id", nullable = false)
    private SourceSet sourceSet;

    /** 문서 ID (infra.rag_document.id) */
    @Column(name = "document_id", columnDefinition = "uuid", nullable = false)
    private UUID documentId;

    /** 생성 시각 */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 문서 처리 상태: PENDING, PROCESSING, COMPLETED, FAILED */
    @Column(name = "status", length = 20)
    private String status;

    /** 에러 코드 (실패 시) */
    @Column(name = "error_code", length = 50)
    private String errorCode;

    /** 실패 사유 (실패 시) */
    @Column(name = "fail_reason", length = 1000)
    private String failReason;

    /**
     * 소스셋-문서 관계 생성 팩토리 메서드.
     */
    public static SourceSetDocument create(SourceSet sourceSet, UUID documentId) {
        SourceSetDocument ssd = new SourceSetDocument();
        ssd.setSourceSet(sourceSet);
        ssd.setDocumentId(documentId);
        ssd.setStatus("PENDING");
        return ssd;
    }

    /**
     * 문서 처리 성공 시 상태 업데이트.
     */
    public void markCompleted() {
        this.status = "COMPLETED";
        this.errorCode = null;
        this.failReason = null;
    }

    /**
     * 문서 처리 실패 시 상태 업데이트.
     */
    public void markFailed(String errorCode, String failReason) {
        this.status = "FAILED";
        this.errorCode = errorCode;
        this.failReason = failReason;
    }

    /**
     * 문서 처리 중 상태 업데이트.
     */
    public void markProcessing() {
        this.status = "PROCESSING";
    }
}
