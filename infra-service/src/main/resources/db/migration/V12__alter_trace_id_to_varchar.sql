-- trace_id 컬럼 타입 변경: uuid -> varchar(200)
-- 이유: AI 서버에서 UUID 형식 외에 "trace-{id}" 형식의 문자열도 사용

ALTER TABLE telemetry.telemetry_event
ALTER COLUMN trace_id TYPE varchar(200)
USING trace_id::varchar;

COMMENT ON COLUMN telemetry.telemetry_event.trace_id IS 'X-Trace-Id (UUID 또는 문자열)';
