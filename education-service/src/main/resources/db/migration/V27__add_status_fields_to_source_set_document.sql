-- SourceSetDocument에 상태 필드 추가
ALTER TABLE education.source_set_document 
ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'PENDING',
ADD COLUMN IF NOT EXISTS error_code VARCHAR(50),
ADD COLUMN IF NOT EXISTS fail_reason VARCHAR(1000);

COMMENT ON COLUMN education.source_set_document.status IS '문서 처리 상태: PENDING, PROCESSING, COMPLETED, FAILED';
COMMENT ON COLUMN education.source_set_document.error_code IS '에러 코드 (실패 시)';
COMMENT ON COLUMN education.source_set_document.fail_reason IS '실패 사유 (실패 시)';

-- 기존 데이터는 COMPLETED로 설정 (이미 처리 완료된 것으로 간주)
UPDATE education.source_set_document SET status = 'COMPLETED' WHERE status IS NULL;

