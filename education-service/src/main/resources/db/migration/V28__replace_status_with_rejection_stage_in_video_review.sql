-- education_video_review 테이블의 status 컬럼을 rejection_stage로 변경
-- status는 항상 REJECTED였으므로, rejection_stage로 어떤 단계에서 반려되었는지 저장

-- 1. rejection_stage 컬럼 추가
ALTER TABLE education.education_video_review 
ADD COLUMN IF NOT EXISTS rejection_stage VARCHAR(20);

-- 2. 기존 데이터 마이그레이션 (status가 REJECTED였던 모든 레코드)
-- rejection_stage는 나중에 애플리케이션에서 설정하거나, 기본값으로 'UNKNOWN' 설정
UPDATE education.education_video_review 
SET rejection_stage = 'UNKNOWN' 
WHERE rejection_stage IS NULL;

-- 3. status 컬럼 제거
ALTER TABLE education.education_video_review 
DROP COLUMN IF EXISTS status;

-- 4. rejection_stage에 NOT NULL 제약 추가
ALTER TABLE education.education_video_review 
ALTER COLUMN rejection_stage SET NOT NULL;

-- 5. 컬럼 설명 추가
COMMENT ON COLUMN education.education_video_review.rejection_stage IS '반려 단계: SCRIPT(스크립트 검토 단계 반려), VIDEO(영상 검토 단계 반려)';

