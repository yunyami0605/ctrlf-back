CREATE SCHEMA IF NOT EXISTS telemetry;
SET search_path = telemetry;

-- 텔레메트리 이벤트 테이블
CREATE TABLE IF NOT EXISTS "telemetry_event" (
  "event_id" uuid PRIMARY KEY,
  "source" varchar(50) NOT NULL,
  "sent_at" timestamptz NOT NULL,
  "event_type" varchar(30) NOT NULL,
  "trace_id" uuid NOT NULL,
  "conversation_id" varchar(100),
  "turn_id" int,
  "user_id" varchar(64) NOT NULL,
  "dept_id" varchar(64) NOT NULL,
  "occurred_at" timestamptz NOT NULL,
  "payload" jsonb NOT NULL,
  "received_at" timestamptz NOT NULL DEFAULT now()
);

-- 인덱스 생성
CREATE INDEX IF NOT EXISTS "idx_telemetry_event_occurred_at" ON "telemetry_event" ("occurred_at");
CREATE INDEX IF NOT EXISTS "idx_telemetry_event_dept_occurred" ON "telemetry_event" ("dept_id", "occurred_at");
CREATE INDEX IF NOT EXISTS "idx_telemetry_event_type_occurred" ON "telemetry_event" ("event_type", "occurred_at");
CREATE INDEX IF NOT EXISTS "idx_telemetry_event_conversation_turn" ON "telemetry_event" ("conversation_id", "turn_id");

-- 코멘트 추가
COMMENT ON TABLE "telemetry_event" IS 'AI에서 수집한 텔레메트리 이벤트';
COMMENT ON COLUMN "telemetry_event"."event_id" IS 'Idempotency key (AI eventId)';
COMMENT ON COLUMN "telemetry_event"."source" IS 'ai-gateway 등';
COMMENT ON COLUMN "telemetry_event"."sent_at" IS 'AI가 전송한 시각';
COMMENT ON COLUMN "telemetry_event"."event_type" IS 'CHAT_TURN | FEEDBACK | SECURITY';
COMMENT ON COLUMN "telemetry_event"."trace_id" IS 'X-Trace-Id';
COMMENT ON COLUMN "telemetry_event"."conversation_id" IS 'X-Conversation-Id';
COMMENT ON COLUMN "telemetry_event"."turn_id" IS 'X-Turn-Id';
COMMENT ON COLUMN "telemetry_event"."user_id" IS 'X-User-Id (또는 user_uuid)';
COMMENT ON COLUMN "telemetry_event"."dept_id" IS 'X-Dept-Id';
COMMENT ON COLUMN "telemetry_event"."occurred_at" IS 'occurredAt';
COMMENT ON COLUMN "telemetry_event"."payload" IS 'eventType별 payload 원문';
COMMENT ON COLUMN "telemetry_event"."received_at" IS '백엔드 수신 시각';

