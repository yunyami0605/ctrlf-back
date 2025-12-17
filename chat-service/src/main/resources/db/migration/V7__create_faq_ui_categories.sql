CREATE TABLE IF NOT EXISTS chat.faq_ui_categories (
  id UUID PRIMARY KEY,
  slug VARCHAR(50) NOT NULL UNIQUE,
  display_name VARCHAR(100) NOT NULL,
  sort_order INT NOT NULL,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_by UUID NULL,
  updated_by UUID NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ✅ ETC 기본 카테고리 시드 (불변/필수)
-- UUID는 고정값으로 박아두는 걸 추천 (환경마다 동일)
INSERT INTO chat.faq_ui_categories (id, slug, display_name, sort_order, is_active)
VALUES ('00000000-0000-0000-0000-000000000999', 'ETC', '기타', 999, true)
ON CONFLICT (slug) DO NOTHING;
