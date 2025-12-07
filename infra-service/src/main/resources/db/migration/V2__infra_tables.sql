CREATE SCHEMA IF NOT EXISTS infra;
SET search_path = infra;
-- Extensions consolidated here
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS "user_profile" (
  "id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  "user_uuid" uuid UNIQUE,
  "employee_id" varchar(50),
  "department" varchar(100),
  "position" varchar(50),
  "gender" varchar(10),
  "age" int,
  "created_at" timestamp,
  "updated_at" timestamp
);

CREATE TABLE IF NOT EXISTS "system_log" (
  "id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  "service_name" varchar(50),
  "event_type" varchar(50),
  "level" varchar(20),
  "summary" text,
  "raw_ref" varchar(255),
  "trace_id" varchar(100),
  "created_at" timestamp
);

CREATE TABLE IF NOT EXISTS "service_event_log" (
  "id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  "service_name" varchar(50),
  "entity_type" varchar(50),
  "entity_id" uuid,
  "event_type" varchar(50),
  "old_value" varchar(255),
  "new_value" varchar(255),
  "changed_by" varchar(50),
  "trace_id" varchar(100),
  "created_at" timestamp
);

CREATE TABLE IF NOT EXISTS "error_event_log" (
  "id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  "service_name" varchar(50),
  "error_type" varchar(50),
  "summary" text,
  "stack_ref" varchar(255),
  "trace_id" varchar(100),
  "created_at" timestamp
);

CREATE TABLE IF NOT EXISTS "rag_document" (
  "id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  "title" varchar(255),
  "domain" varchar(50),
  "uploader_uuid" char(36),
  "source_url" varchar(255),
  "created_at" timestamp
);

CREATE TABLE IF NOT EXISTS "rag_document_chunk" (
  "id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  "document_id" uuid,
  "chunk_index" int,
  "chunk_text" text,
  "embedding" vector(1536),
  "created_at" timestamp
);

CREATE TABLE IF NOT EXISTS "rag_fail_chunk" (
  "id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  "document_id" uuid,
  "chunk_index" int,
  "fail_reason" text,
  "created_at" timestamp
);

ALTER TABLE "rag_document_chunk" ADD FOREIGN KEY ("document_id") REFERENCES "rag_document" ("id");
ALTER TABLE "rag_fail_chunk" ADD FOREIGN KEY ("document_id") REFERENCES "rag_document" ("id");


