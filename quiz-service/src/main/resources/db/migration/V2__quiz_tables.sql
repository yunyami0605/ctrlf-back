CREATE SCHEMA IF NOT EXISTS quiz;
SET search_path = quiz;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS "quiz_attempt" (
  "id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  "user_uuid" uuid,
  "education_id" uuid,
  "score" int,
  "passed" boolean,
  "attempt_no" int,
  "created_at" timestamp,
  "time_limit" int,
  "submitted_at" timestamp
);

CREATE TABLE IF NOT EXISTS "quiz_question" (
  "id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  "attempt_id" uuid,
  "question" text,
  "options" text,
  "correct_option_idx" int,
  "explanation" text,
  "user_selected_option_idx" int
);

CREATE TABLE IF NOT EXISTS "quiz_leave_tracking" (
  "id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  "attempt_id" uuid,
  "leave_count" int,
  "total_leave_seconds" int,
  "last_leave_at" timestamp
);

ALTER TABLE "quiz_question" ADD FOREIGN KEY ("attempt_id") REFERENCES "quiz_attempt" ("id");
ALTER TABLE "quiz_leave_tracking" ADD FOREIGN KEY ("attempt_id") REFERENCES "quiz_attempt" ("id");


