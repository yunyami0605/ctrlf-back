-- education_video 테이블에 updated_at 컬럼 추가
ALTER TABLE education.education_video
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

COMMENT ON COLUMN education.education_video.updated_at IS '최근 수정 시각';

