-- V11: 검토 반려 관련 필드 추가

ALTER TABLE infra.rag_document 
    ADD COLUMN IF NOT EXISTS reject_reason text;

ALTER TABLE infra.rag_document 
    ADD COLUMN IF NOT EXISTS rejected_at timestamp;

COMMENT ON COLUMN infra.rag_document.reject_reason IS '반려 사유';
COMMENT ON COLUMN infra.rag_document.rejected_at IS '반려 시각';

