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
