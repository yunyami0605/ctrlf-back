-- Add soft delete column to all education tables
ALTER TABLE education.education            ADD COLUMN IF NOT EXISTS deleted_at timestamp;
ALTER TABLE education.education_source_doc ADD COLUMN IF NOT EXISTS deleted_at timestamp;
ALTER TABLE education.education_video_progress ADD COLUMN IF NOT EXISTS deleted_at timestamp;
ALTER TABLE education.education_script     ADD COLUMN IF NOT EXISTS deleted_at timestamp;
ALTER TABLE education.video_generation_job ADD COLUMN IF NOT EXISTS deleted_at timestamp;
ALTER TABLE education.education_video      ADD COLUMN IF NOT EXISTS deleted_at timestamp;
ALTER TABLE education.education_progress   ADD COLUMN IF NOT EXISTS deleted_at timestamp;
ALTER TABLE education.education_video_review ADD COLUMN IF NOT EXISTS deleted_at timestamp;


