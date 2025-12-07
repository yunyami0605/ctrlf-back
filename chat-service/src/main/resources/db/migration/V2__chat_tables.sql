CREATE SCHEMA IF NOT EXISTS chat;
SET search_path = chat;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS "chat_session" (
  "id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  "user_uuid" uuid,
  "title" varchar(200),
  "domain" varchar(50),
  "created_at" timestamp,
  "updated_at" timestamp,
  "deleted" boolean
);

CREATE TABLE IF NOT EXISTS "chat_section" (
  "id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  "session_id" uuid,
  "title" varchar(200),
  "summary" text,
  "retry_count" int,
  "created_at" timestamp,
  "closed_at" timestamp
);

CREATE TABLE IF NOT EXISTS "chat_message" (
  "id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  "session_id" uuid,
  "section_id" uuid,
  "role" varchar(20),
  "content" text,
  "tokens_in" int,
  "tokens_out" int,
  "llm_model" varchar(50),
  "created_at" timestamp
);

CREATE TABLE IF NOT EXISTS "chat_feedback" (
  "id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  "session_id" uuid,
  "section_id" uuid,
  "message_id" uuid,
  "user_uuid" uuid,
  "score" int,
  "comment" varchar(500),
  "created_at" timestamp
);

CREATE TABLE IF NOT EXISTS "chat_session_feedback" (
  "id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  "session_id" uuid,
  "user_uuid" uuid,
  "score" int,
  "comment" text,
  "created_at" timestamp
);

ALTER TABLE "chat_section" ADD FOREIGN KEY ("session_id") REFERENCES "chat_session" ("id");
ALTER TABLE "chat_message" ADD FOREIGN KEY ("session_id") REFERENCES "chat_session" ("id");
ALTER TABLE "chat_message" ADD FOREIGN KEY ("section_id") REFERENCES "chat_section" ("id");
ALTER TABLE "chat_feedback" ADD FOREIGN KEY ("session_id") REFERENCES "chat_session" ("id");
ALTER TABLE "chat_feedback" ADD FOREIGN KEY ("section_id") REFERENCES "chat_section" ("id");
ALTER TABLE "chat_feedback" ADD FOREIGN KEY ("message_id") REFERENCES "chat_message" ("id");
ALTER TABLE "chat_session_feedback" ADD FOREIGN KEY ("session_id") REFERENCES "chat_session" ("id");

-- FAQ tables moved to chat (consolidated into V2)
CREATE TABLE IF NOT EXISTS "faq" (
  "id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  "question" text,
  "answer" text,
  "domain" varchar(20),
  "is_active" boolean,
  "priority" int,
  "created_at" timestamp,
  "updated_at" timestamp
);

CREATE TABLE IF NOT EXISTS "faq_candidate" (
  "id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  "question" text,
  "domain" varchar(50),
  "frequency" int,
  "score" double precision,
  "is_disabled" boolean,
  "created_at" timestamp
);


