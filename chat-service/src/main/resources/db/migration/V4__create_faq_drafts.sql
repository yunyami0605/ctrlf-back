-- FAQ AI Draft 테이블 생성 (Phase18)

CREATE TABLE IF NOT EXISTS chat.faq_drafts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- AI Gateway에서 내려준 draft ID
    faq_draft_id VARCHAR(100) NOT NULL UNIQUE,

    -- 도메인
    domain VARCHAR(50) NOT NULL,

    -- 후보 클러스터 ID
    cluster_id VARCHAR(100) NOT NULL,

    -- FAQ 내용
    question TEXT NOT NULL,
    answer_markdown TEXT NOT NULL,
    summary TEXT,

    -- AI 신뢰도
    ai_confidence DOUBLE PRECISION,

    -- 상태
    status VARCHAR(20) NOT NULL,

    -- 관리자 정보
    reviewer_id UUID,
    published_at TIMESTAMP,

    -- 생성 시각
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
-- FAQ 관리자 수정 이력 테이블

CREATE TABLE IF NOT EXISTS chat.faq_revisions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    target_type VARCHAR(20) NOT NULL,
    target_id UUID NOT NULL,

    action VARCHAR(30) NOT NULL,

    actor_id UUID,
    reason TEXT,
    snapshot TEXT,

    created_at TIMESTAMP NOT NULL DEFAULT now()
);
