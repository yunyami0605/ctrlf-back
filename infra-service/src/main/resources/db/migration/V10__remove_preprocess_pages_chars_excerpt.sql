-- V10: 전처리 관련 불필요한 컬럼 제거 (AI 서버에서 제공하지 않음)

ALTER TABLE infra.rag_document 
    DROP COLUMN IF EXISTS preprocess_pages;

ALTER TABLE infra.rag_document 
    DROP COLUMN IF EXISTS preprocess_chars;

ALTER TABLE infra.rag_document 
    DROP COLUMN IF EXISTS preprocess_excerpt;

