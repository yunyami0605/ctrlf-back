-- Phase 56: 부서별 교육영상 필터링을 위한 department 컬럼 추가
-- 부서 범위: 전체 부서, 총무팀, 기획팀, 마케팅팀, 인사팀, 재무팀, 개발팀, 영업팀, 법무팀

ALTER TABLE infra.rag_document
ADD COLUMN IF NOT EXISTS department VARCHAR(32);

COMMENT ON COLUMN infra.rag_document.department IS '부서 범위 (전체 부서, 총무팀, 기획팀, 마케팅팀, 인사팀, 재무팀, 개발팀, 영업팀, 법무팀)';
