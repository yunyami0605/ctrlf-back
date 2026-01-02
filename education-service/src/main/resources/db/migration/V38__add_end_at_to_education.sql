-- Add start_at and end_at columns to education table
-- start_at과 end_at을 함께 추가 (IF NOT EXISTS로 이미 존재하면 스킵)

ALTER TABLE education.education
    ADD COLUMN IF NOT EXISTS start_at timestamptz;

ALTER TABLE education.education
    ADD COLUMN IF NOT EXISTS end_at timestamptz;

