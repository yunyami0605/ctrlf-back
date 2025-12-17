-- Phase18 대응: faq_candidate 컬럼 확장
-- 기존 테이블은 유지하고, 필요한 컬럼만 추가한다.

ALTER TABLE chat.faq_candidate
    -- 대표 질문 (canonical question)
    ADD COLUMN canonical_question TEXT,

    -- 최근 7일 질문 수
    ADD COLUMN question_count_7d INTEGER,

    -- 최근 30일 질문 수
    ADD COLUMN question_count_30d INTEGER,

    -- 평균 의도 신뢰도
    ADD COLUMN avg_intent_confidence DOUBLE PRECISION,

    -- PII 감지 여부
    ADD COLUMN pii_detected BOOLEAN NOT NULL DEFAULT FALSE,

    -- 후보 점수
    ADD COLUMN score_candidate DOUBLE PRECISION,

    -- 후보 상태
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'NEW',

    -- 마지막 질문 시각
    ADD COLUMN last_asked_at TIMESTAMP;

-- 기존 데이터 보정 (NULL 방지)
UPDATE chat.faq_candidate
SET
    canonical_question = COALESCE(canonical_question, question),
    status = COALESCE(status, 'NEW');
