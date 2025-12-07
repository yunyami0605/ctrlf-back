CREATE SCHEMA IF NOT EXISTS education;
SET search_path = education;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS "education" (
  "id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  "title" varchar(255),
  "category" varchar(50),
  "department_scope" text,
  "description" text,
  "pass_score" int,
  "pass_ratio" int,
  "require" boolean,
  "created_at" timestamp,
  "updated_at" timestamp
);

CREATE TABLE IF NOT EXISTS "education_source_doc" (
  "id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  "education_id" uuid,
  "uploader_uuid" uuid,
  "file_url" varchar(255),
  "file_type" varchar(20),
  "page_count" int,
  "created_at" timestamp
);

CREATE TABLE IF NOT EXISTS "education_video_progress" (
  "id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  "user_uuid" uuid,
  "education_id" uuid,
  "video_id" uuid,
  "progress" int,
  "last_position_seconds" int,
  "total_watch_seconds" int,
  "is_completed" boolean,
  "updated_at" timestamp,
  "created_at" timestamp
);

CREATE TABLE IF NOT EXISTS "education_script" (
  "id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  "education_id" uuid,
  "source_doc_id" uuid,
  "version" int,
  "content" text,
  "created_by" uuid,
  "created_at" timestamp
);

CREATE TABLE IF NOT EXISTS "video_generation_job" (
  "id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  "education_id" uuid,
  "script_id" uuid,
  "template_option" json,
  "status" varchar(20),
  "fail_reason" text,
  "generated_video_url" varchar(255),
  "retry_count" int,
  "created_at" timestamp,
  "updated_at" timestamp
);

CREATE TABLE IF NOT EXISTS "education_video" (
  "id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  "education_id" uuid,
  "generation_job_id" uuid,
  "file_url" varchar(255),
  "version" int,
  "duration" int,
  "is_main" boolean,
  "status" varchar(50),
  "target_dept_code" varchar(50),
  "created_at" timestamp
);

CREATE TABLE IF NOT EXISTS "education_progress" (
  "id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  "user_uuid" uuid,
  "education_id" uuid,
  "progress" int,
  "is_completed" boolean,
  "completed_at" timestamp,
  "updated_at" timestamp,
  "last_position_seconds" int,
  "total_watch_seconds" int
);

CREATE TABLE IF NOT EXISTS "education_video_review" (
  "id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  "video_id" uuid,
  "reviewer_uuid" uuid,
  "status" varchar(50),
  "comment" text,
  "created_at" timestamp
);

ALTER TABLE "education_source_doc" ADD FOREIGN KEY ("education_id") REFERENCES "education" ("id");
ALTER TABLE "education_video_progress" ADD FOREIGN KEY ("education_id") REFERENCES "education" ("id");
ALTER TABLE "education_video_progress" ADD FOREIGN KEY ("video_id") REFERENCES "education_video" ("id");
ALTER TABLE "education_script" ADD FOREIGN KEY ("education_id") REFERENCES "education" ("id");
ALTER TABLE "education_script" ADD FOREIGN KEY ("source_doc_id") REFERENCES "education_source_doc" ("id");
ALTER TABLE "video_generation_job" ADD FOREIGN KEY ("education_id") REFERENCES "education" ("id");
ALTER TABLE "video_generation_job" ADD FOREIGN KEY ("script_id") REFERENCES "education_script" ("id");
ALTER TABLE "education_video" ADD FOREIGN KEY ("education_id") REFERENCES "education" ("id");
ALTER TABLE "education_video" ADD FOREIGN KEY ("generation_job_id") REFERENCES "video_generation_job" ("id");
ALTER TABLE "education_progress" ADD FOREIGN KEY ("education_id") REFERENCES "education" ("id");
ALTER TABLE "education_video_review" ADD FOREIGN KEY ("video_id") REFERENCES "education_video" ("id");


