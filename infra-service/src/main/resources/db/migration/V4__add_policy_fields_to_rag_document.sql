-- V4: RagDocument에 사규 관리용 필드 추가
-- document_id, version, change_summary 필드 추가

ALTER TABLE infra.rag_document
    ADD COLUMN IF NOT EXISTS document_id varchar(50);

ALTER TABLE infra.rag_document
    ADD COLUMN IF NOT EXISTS version integer;

ALTER TABLE infra.rag_document
    ADD COLUMN IF NOT EXISTS change_summary text;

-- document_id에 유니크 인덱스 추가 (NULL은 제외)
CREATE UNIQUE INDEX IF NOT EXISTS idx_rag_document_document_id 
    ON infra.rag_document(document_id) 
    WHERE document_id IS NOT NULL;

COMMENT ON COLUMN infra.rag_document.document_id IS '사규 문서 ID (예: POL-EDU-015)';
COMMENT ON COLUMN infra.rag_document.version IS '문서 버전 번호';
COMMENT ON COLUMN infra.rag_document.change_summary IS '변경 요약 (사규 관리용)';

