-- education_video 테이블에 제작자 UUID 컬럼 추가
ALTER TABLE education.education_video
ADD COLUMN IF NOT EXISTS creator_uuid UUID;

COMMENT ON COLUMN education.education_video.creator_uuid IS '영상 제작자 UUID';

