package com.ctrlf.infra.rag.entity;

/**
 * RAG 문서 상태 열거형.
 * 
 * RAG 문서 처리 상태와 사규 관리 상태를 모두 포함합니다.
 */
public enum RagDocumentStatus {
    // ==================== RAG 문서 처리 상태 ====================
    /** 대기 중 (임베딩 처리 대기) */
    QUEUED,
    
    /** 처리 중 (임베딩 처리 진행 중) */
    PROCESSING,
    
    /** 처리 완료 (임베딩 처리 성공) */
    COMPLETED,
    
    /** 처리 실패 (임베딩 처리 실패) */
    FAILED,
    
    /** 재처리 중 (재처리 요청됨) */
    REPROCESSING,
    
    // ==================== 사규 관리 상태 ====================
    /** 활성 (현재 사용 중인 사규) */
    ACTIVE,
    
    /** 초안 (작성 중인 사규) */
    DRAFT,
    
    /** 대기 (승인 대기 중인 사규) */
    PENDING,
    
    /** 반려됨 (검토 후 반려된 사규) */
    REJECTED,
    
    /** 보관됨 (더 이상 사용하지 않는 사규) */
    ARCHIVED;

    /**
     * 문자열을 RagDocumentStatus로 변환합니다.
     * 
     * @param status 상태 문자열
     * @return RagDocumentStatus (null이거나 유효하지 않은 경우 null)
     */
    public static RagDocumentStatus fromString(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * RAG 문서 처리 상태인지 확인합니다.
     * 
     * @return RAG 문서 처리 상태이면 true
     */
    public boolean isRagProcessingStatus() {
        return this == QUEUED || this == PROCESSING || 
               this == COMPLETED || this == FAILED || 
               this == REPROCESSING;
    }

    /**
     * 사규 관리 상태인지 확인합니다.
     * 
     * @return 사규 관리 상태이면 true
     */
    public boolean isPolicyManagementStatus() {
        return this == ACTIVE || this == DRAFT || 
               this == PENDING || this == ARCHIVED || 
               this == REJECTED;
    }
}

