-- V19: 채팅 피드백 UPSERT 지원을 위한 스키마 개선
-- 목적: (message_id, user_uuid) 조합당 최신 1건만 유지

-- 0. pgcrypto extension 활성화 (gen_random_uuid() 사용을 위해)
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 1. updated_at 컬럼 추가 (TIMESTAMPTZ로 타임존 정합성 확보)
ALTER TABLE chat.chat_feedback
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ;

-- 2. 기존 row의 updated_at 백필 (NULL 방지)
UPDATE chat.chat_feedback
SET updated_at = created_at
WHERE updated_at IS NULL;

-- 3. 중복 데이터 정리 (최신 1건만 유지)
--    윈도우 함수로 그룹 내 순위 매기고, 1등 외 삭제
DELETE FROM chat.chat_feedback
WHERE id IN (
    SELECT id FROM (
        SELECT id,
               ROW_NUMBER() OVER (
                   PARTITION BY message_id, user_uuid
                   ORDER BY COALESCE(updated_at, created_at) DESC,
                            created_at DESC,
                            id DESC
               ) AS rn
        FROM chat.chat_feedback
    ) ranked
    WHERE rn > 1
);

-- 4. updated_at NOT NULL 제약 + DEFAULT 추가 (데이터 품질 강제)
ALTER TABLE chat.chat_feedback
ALTER COLUMN updated_at SET NOT NULL,
ALTER COLUMN updated_at SET DEFAULT NOW();

-- 5. UNIQUE 제약조건 추가 (UPSERT 핵심)
ALTER TABLE chat.chat_feedback
ADD CONSTRAINT uq_chat_feedback_message_user
UNIQUE (message_id, user_uuid);

-- 6. 인덱스 추가 (집계 쿼리 성능)
CREATE INDEX IF NOT EXISTS idx_chat_feedback_created_at
ON chat.chat_feedback(created_at);
