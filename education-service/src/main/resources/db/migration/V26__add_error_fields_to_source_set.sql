-- SourceSet에 에러 정보 필드 추가
ALTER TABLE education.source_set 
ADD COLUMN IF NOT EXISTS error_code VARCHAR(50),
ADD COLUMN IF NOT EXISTS fail_reason VARCHAR(1000);

COMMENT ON COLUMN education.source_set.error_code IS '에러 코드 (실패 시)';
COMMENT ON COLUMN education.source_set.fail_reason IS '실패 사유 (실패 시)';

