CREATE SCHEMA "infra";

CREATE SCHEMA "chat";

CREATE SCHEMA "education";

CREATE TABLE "infra"."user_profile" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "user_uuid" uuid UNIQUE,
  "employee_id" varchar(50),
  "department" varchar(100),
  "position" varchar(50),
  "gender" varchar(10),
  "age" int,
  "created_at" timestamp,
  "updated_at" timestamp
);

CREATE TABLE "infra"."system_log" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "service_name" varchar(50),
  "event_type" varchar(50),
  "level" varchar(20),
  "summary" text,
  "raw_ref" varchar(255),
  "trace_id" varchar(100),
  "created_at" timestamp
);

CREATE TABLE "infra"."service_event_log" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
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

CREATE TABLE "infra"."error_event_log" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "service_name" varchar(50),
  "error_type" varchar(50),
  "summary" text,
  "stack_ref" varchar(255),
  "trace_id" varchar(100),
  "created_at" timestamp
);

CREATE TABLE "infra"."rag_document" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "title" varchar(255),
  "domain" varchar(50),
  "uploader_uuid" char(36),
  "source_url" varchar(255),
  "created_at" timestamp
);

CREATE TABLE "infra"."rag_document_chunk" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "document_id" uuid,
  "chunk_index" int,
  "chunk_text" text,
  "embedding" vector(1536),
  "created_at" timestamp
);

CREATE TABLE "infra"."rag_fail_chunk" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "document_id" uuid,
  "chunk_index" int,
  "fail_reason" text,
  "created_at" timestamp
);

CREATE TABLE "chat"."chat_session" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "user_uuid" uuid,
  "title" varchar(200),
  "domain" varchar(50),
  "created_at" timestamp,
  "updated_at" timestamp,
  "deleted" boolean
);

CREATE TABLE "chat"."chat_section" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "session_id" uuid,
  "title" varchar(200),
  "summary" text,
  "retry_count" int,
  "created_at" timestamp,
  "closed_at" timestamp
);

CREATE TABLE "chat"."chat_message" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "session_id" uuid,
  "section_id" uuid,
  "role" varchar(20),
  "content" text,
  "tokens_in" int,
  "tokens_out" int,
  "llm_model" varchar(50),
  "created_at" timestamp
);

CREATE TABLE "chat"."chat_feedback" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "session_id" uuid,
  "section_id" uuid,
  "message_id" uuid,
  "user_uuid" uuid,
  "score" int,
  "comment" varchar(500),
  "created_at" timestamp
);

CREATE TABLE "chat"."chat_session_feedback" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "session_id" uuid,
  "user_uuid" uuid,
  "score" int,
  "comment" text,
  "created_at" timestamp
);

CREATE TABLE "chat"."faq" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "question" text,
  "answer" text,
  "domain" varchar(20),
  "is_active" boolean,
  "priority" int,
  "created_at" timestamp,
  "updated_at" timestamp
);

CREATE TABLE "chat"."faq_candidate" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "question" text,
  "domain" varchar(50),
  "frequency" int,
  "score" float,
  "is_disabled" boolean,
  "created_at" timestamp
);

CREATE TABLE "education"."education" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
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

CREATE TABLE "education"."education_source_doc" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "education_id" uuid,
  "uploader_uuid" uuid,
  "file_url" varchar(255),
  "file_type" varchar(20),
  "page_count" int,
  "created_at" timestamp
);

CREATE TABLE "education"."education_video" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
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

CREATE TABLE "education"."education_video_progress" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
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

CREATE TABLE "education"."education_script" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "education_id" uuid,
  "source_doc_id" uuid,
  "version" int,
  "content" text,
  "created_by" uuid,
  "created_at" timestamp
);

CREATE TABLE "education"."video_generation_job" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
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

CREATE TABLE "education"."education_progress" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "user_uuid" uuid,
  "education_id" uuid,
  "progress" int,
  "is_completed" boolean,
  "completed_at" timestamp,
  "updated_at" timestamp,
  "last_position_seconds" int,
  "total_watch_seconds" int
);

CREATE TABLE "education"."education_video_review" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "video_id" uuid,
  "reviewer_uuid" uuid,
  "status" varchar(50),
  "comment" text,
  "created_at" timestamp
);

CREATE TABLE "education"."quiz_attempt" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "user_uuid" uuid,
  "education_id" uuid,
  "score" int,
  "passed" boolean,
  "attempt_no" int,
  "created_at" timestamp,
  "time_limit" int,
  "submitted_at" timestamp
);

CREATE TABLE "education"."quiz_question" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "attempt_id" uuid,
  "question" text,
  "options" text,
  "correct_option_idx" int,
  "explanation" text,
  "user_selected_option_idx" int
);

CREATE TABLE "education"."quiz_leave_tracking" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "attempt_id" uuid,
  "leave_count" int,
  "total_leave_seconds" int,
  "last_leave_at" timestamp
);

COMMENT ON COLUMN "infra"."user_profile"."id" IS '프로필 PK';

COMMENT ON COLUMN "infra"."user_profile"."user_uuid" IS 'Keycloak 사용자 UUID(sub)';

COMMENT ON COLUMN "infra"."user_profile"."employee_id" IS '사번 또는 회사 로그인 ID';

COMMENT ON COLUMN "infra"."user_profile"."department" IS '부서명';

COMMENT ON COLUMN "infra"."user_profile"."position" IS '직급(사원/대리/과장 등)';

COMMENT ON COLUMN "infra"."user_profile"."gender" IS '선택 입력 - 성별';

COMMENT ON COLUMN "infra"."user_profile"."age" IS '선택 입력 - 나이';

COMMENT ON COLUMN "infra"."user_profile"."created_at" IS '생성 시각';

COMMENT ON COLUMN "infra"."user_profile"."updated_at" IS '수정 시각';

COMMENT ON COLUMN "infra"."system_log"."id" IS '운영 요약 로그 PK';

COMMENT ON COLUMN "infra"."system_log"."service_name" IS '서비스명(chat/quiz/rag 등)';

COMMENT ON COLUMN "infra"."system_log"."event_type" IS '이벤트 유형';

COMMENT ON COLUMN "infra"."system_log"."level" IS 'INFO/WARN/ERROR';

COMMENT ON COLUMN "infra"."system_log"."summary" IS '요약된 시스템 로그';

COMMENT ON COLUMN "infra"."system_log"."raw_ref" IS '원본 로그 저장 경로(S3 등)';

COMMENT ON COLUMN "infra"."system_log"."trace_id" IS '요청 단위 추적 ID';

COMMENT ON COLUMN "infra"."system_log"."created_at" IS '생성 시각';

COMMENT ON COLUMN "infra"."service_event_log"."id" IS '비즈니스 이벤트 로그 PK';

COMMENT ON COLUMN "infra"."service_event_log"."service_name" IS '서비스명';

COMMENT ON COLUMN "infra"."service_event_log"."entity_type" IS '엔티티 종류';

COMMENT ON COLUMN "infra"."service_event_log"."entity_id" IS '엔티티 PK';

COMMENT ON COLUMN "infra"."service_event_log"."event_type" IS '이벤트 유형';

COMMENT ON COLUMN "infra"."service_event_log"."old_value" IS '변경 전 값';

COMMENT ON COLUMN "infra"."service_event_log"."new_value" IS '변경 후 값';

COMMENT ON COLUMN "infra"."service_event_log"."changed_by" IS '수행자 UUID';

COMMENT ON COLUMN "infra"."service_event_log"."trace_id" IS 'Trace ID';

COMMENT ON COLUMN "infra"."service_event_log"."created_at" IS '생성 시각';

COMMENT ON COLUMN "infra"."error_event_log"."id" IS '오류 로그 PK';

COMMENT ON COLUMN "infra"."error_event_log"."service_name" IS '서비스명';

COMMENT ON COLUMN "infra"."error_event_log"."error_type" IS '오류 타입(LLM_ERROR 등)';

COMMENT ON COLUMN "infra"."error_event_log"."summary" IS '요약된 에러 메시지';

COMMENT ON COLUMN "infra"."error_event_log"."stack_ref" IS 'Stacktrace S3 링크';

COMMENT ON COLUMN "infra"."error_event_log"."trace_id" IS '트레이스 ID';

COMMENT ON COLUMN "infra"."error_event_log"."created_at" IS '생성 시각';

COMMENT ON COLUMN "infra"."rag_document"."id" IS 'RAG 문서 PK';

COMMENT ON COLUMN "infra"."rag_document"."title" IS '문서 제목';

COMMENT ON COLUMN "infra"."rag_document"."domain" IS '문서 도메인(HR/보안/직무/개발 등)';

COMMENT ON COLUMN "infra"."rag_document"."uploader_uuid" IS '업로더 UUID';

COMMENT ON COLUMN "infra"."rag_document"."source_url" IS '원본 파일 URL';

COMMENT ON COLUMN "infra"."rag_document"."created_at" IS '문서 등록 시각';

COMMENT ON COLUMN "infra"."rag_document_chunk"."id" IS '문서 청크 PK';

COMMENT ON COLUMN "infra"."rag_document_chunk"."document_id" IS '원본 문서 ID';

COMMENT ON COLUMN "infra"."rag_document_chunk"."chunk_index" IS '청크 번호';

COMMENT ON COLUMN "infra"."rag_document_chunk"."chunk_text" IS '텍스트 청크 내용';

COMMENT ON COLUMN "infra"."rag_document_chunk"."embedding" IS '임베딩 벡터';

COMMENT ON COLUMN "infra"."rag_document_chunk"."created_at" IS '임베딩 생성 시각';

COMMENT ON COLUMN "infra"."rag_fail_chunk"."id" IS '임베딩 실패 로그 PK';

COMMENT ON COLUMN "infra"."rag_fail_chunk"."document_id" IS '문서 ID';

COMMENT ON COLUMN "infra"."rag_fail_chunk"."chunk_index" IS '실패한 청크 인덱스';

COMMENT ON COLUMN "infra"."rag_fail_chunk"."fail_reason" IS '실패 사유';

COMMENT ON COLUMN "infra"."rag_fail_chunk"."created_at" IS '기록 시각';

COMMENT ON COLUMN "chat"."chat_session"."id" IS '대화 세션(채팅방) PK';

COMMENT ON COLUMN "chat"."chat_session"."user_uuid" IS '대화 생성한 사용자 UUID';

COMMENT ON COLUMN "chat"."chat_session"."title" IS '세션 제목(LLM 또는 사용자 생성)';

COMMENT ON COLUMN "chat"."chat_session"."domain" IS '업무 도메인(FAQ/보안/직무/상담/일반 등)';

COMMENT ON COLUMN "chat"."chat_session"."created_at" IS '세션 생성 시각';

COMMENT ON COLUMN "chat"."chat_session"."updated_at" IS '마지막 메시지 업데이트 시각';

COMMENT ON COLUMN "chat"."chat_session"."deleted" IS '삭제 여부(소프트 삭제)';

COMMENT ON COLUMN "chat"."chat_section"."id" IS '대화 섹션 PK';

COMMENT ON COLUMN "chat"."chat_section"."session_id" IS '속한 세션 ID';

COMMENT ON COLUMN "chat"."chat_section"."title" IS '섹션 주제 또는 주요 질문';

COMMENT ON COLUMN "chat"."chat_section"."summary" IS 'LLM이 생성한 섹션 요약';

COMMENT ON COLUMN "chat"."chat_section"."retry_count" IS '재질문/재시도 횟수';

COMMENT ON COLUMN "chat"."chat_section"."created_at" IS '섹션 시작 시각';

COMMENT ON COLUMN "chat"."chat_section"."closed_at" IS '섹션 종료 시각';

COMMENT ON COLUMN "chat"."chat_message"."id" IS '메시지 PK';

COMMENT ON COLUMN "chat"."chat_message"."session_id" IS '세션 ID';

COMMENT ON COLUMN "chat"."chat_message"."section_id" IS '섹션 ID';

COMMENT ON COLUMN "chat"."chat_message"."role" IS 'user/assistant/system 역할';

COMMENT ON COLUMN "chat"."chat_message"."content" IS '메시지 내용';

COMMENT ON COLUMN "chat"."chat_message"."tokens_in" IS '입력 토큰 수';

COMMENT ON COLUMN "chat"."chat_message"."tokens_out" IS '출력 토큰 수';

COMMENT ON COLUMN "chat"."chat_message"."llm_model" IS '사용된 LLM 모델명';

COMMENT ON COLUMN "chat"."chat_message"."created_at" IS '메시지 생성 시각';

COMMENT ON COLUMN "chat"."chat_feedback"."id" IS '메시지 피드백 PK';

COMMENT ON COLUMN "chat"."chat_feedback"."session_id" IS '세션 ID';

COMMENT ON COLUMN "chat"."chat_feedback"."section_id" IS '섹션 ID';

COMMENT ON COLUMN "chat"."chat_feedback"."message_id" IS '피드백 대상 메시지 ID';

COMMENT ON COLUMN "chat"."chat_feedback"."user_uuid" IS '피드백 남긴 사용자';

COMMENT ON COLUMN "chat"."chat_feedback"."score" IS '평점(1~5)';

COMMENT ON COLUMN "chat"."chat_feedback"."comment" IS '선택 코멘트';

COMMENT ON COLUMN "chat"."chat_feedback"."created_at" IS '생성 시각';

COMMENT ON COLUMN "chat"."chat_session_feedback"."id" IS '세션 총평 피드백 PK';

COMMENT ON COLUMN "chat"."chat_session_feedback"."session_id" IS '세션 ID';

COMMENT ON COLUMN "chat"."chat_session_feedback"."user_uuid" IS '피드백 남긴 사용자';

COMMENT ON COLUMN "chat"."chat_session_feedback"."score" IS '세션 전체 만족도(1~5)';

COMMENT ON COLUMN "chat"."chat_session_feedback"."comment" IS '선택 의견';

COMMENT ON COLUMN "chat"."chat_session_feedback"."created_at" IS '평가 시각';

COMMENT ON COLUMN "chat"."faq"."id" IS 'FAQ PK';

COMMENT ON COLUMN "chat"."faq"."question" IS '자주 묻는 질문';

COMMENT ON COLUMN "chat"."faq"."answer" IS '공식 답변';

COMMENT ON COLUMN "chat"."faq"."domain" IS '업무 도메인 분류';

COMMENT ON COLUMN "chat"."faq"."is_active" IS '활성 여부';

COMMENT ON COLUMN "chat"."faq"."priority" IS '노출 우선순위';

COMMENT ON COLUMN "chat"."faq"."created_at" IS '생성 시각';

COMMENT ON COLUMN "chat"."faq"."updated_at" IS '수정 시각';

COMMENT ON COLUMN "chat"."faq_candidate"."id" IS '후보 질문 PK';

COMMENT ON COLUMN "chat"."faq_candidate"."question" IS '사용자가 남긴 원본 질문';

COMMENT ON COLUMN "chat"."faq_candidate"."domain" IS 'LLM이 분류한 도메인';

COMMENT ON COLUMN "chat"."faq_candidate"."frequency" IS '등장 빈도';

COMMENT ON COLUMN "chat"."faq_candidate"."score" IS 'LLM 추천 점수';

COMMENT ON COLUMN "chat"."faq_candidate"."is_disabled" IS '비활성 여부';

COMMENT ON COLUMN "chat"."faq_candidate"."created_at" IS '수집 시각';

COMMENT ON COLUMN "education"."education"."id" IS '교육 PK';

COMMENT ON COLUMN "education"."education"."title" IS '교육 제목';

COMMENT ON COLUMN "education"."education"."category" IS '카테고리(4대/직무/MANDATORY/JOB/ETC)';

COMMENT ON COLUMN "education"."education"."department_scope" IS '수강 가능한 부서 목록(JSON)';

COMMENT ON COLUMN "education"."education"."description" IS '교육 설명';

COMMENT ON COLUMN "education"."education"."pass_score" IS '통과 기준 점수';

COMMENT ON COLUMN "education"."education"."pass_ratio" IS '이수 기준 시청율 %';

COMMENT ON COLUMN "education"."education"."require" IS '필수/선택 여부';

COMMENT ON COLUMN "education"."education"."created_at" IS '생성 시각';

COMMENT ON COLUMN "education"."education"."updated_at" IS '수정 시각';

COMMENT ON COLUMN "education"."education_source_doc"."id" IS '교육 원본 문서 PK';

COMMENT ON COLUMN "education"."education_source_doc"."education_id" IS '교육 ID';

COMMENT ON COLUMN "education"."education_source_doc"."uploader_uuid" IS '문서 업로더 UUID';

COMMENT ON COLUMN "education"."education_source_doc"."file_url" IS '업로드 문서 URL';

COMMENT ON COLUMN "education"."education_source_doc"."file_type" IS '파일 형식(PDF/PPT/HWP 등)';

COMMENT ON COLUMN "education"."education_source_doc"."page_count" IS '페이지 수';

COMMENT ON COLUMN "education"."education_source_doc"."created_at" IS '업로드 시각';

COMMENT ON COLUMN "education"."education_video"."id" IS '교육 영상 PK';

COMMENT ON COLUMN "education"."education_video"."education_id" IS '교육 ID';

COMMENT ON COLUMN "education"."education_video"."generation_job_id" IS '생성 작업 ID';

COMMENT ON COLUMN "education"."education_video"."file_url" IS '영상 URL';

COMMENT ON COLUMN "education"."education_video"."version" IS '영상 버전 번호';

COMMENT ON COLUMN "education"."education_video"."duration" IS '영상 길이(초)';

COMMENT ON COLUMN "education"."education_video"."is_main" IS '대표 영상 여부';

COMMENT ON COLUMN "education"."education_video"."status" IS 'PENDING/REVIEW/APPROVED';

COMMENT ON COLUMN "education"."education_video"."target_dept_code" IS '대상 부서 코드';

COMMENT ON COLUMN "education"."education_video"."created_at" IS '생성 시각';

COMMENT ON COLUMN "education"."education_video_progress"."id" IS '영상별 시청 진행도 PK';

COMMENT ON COLUMN "education"."education_video_progress"."user_uuid" IS '사용자 UUID';

COMMENT ON COLUMN "education"."education_video_progress"."education_id" IS '교육 ID';

COMMENT ON COLUMN "education"."education_video_progress"."video_id" IS '영상 ID';

COMMENT ON COLUMN "education"."education_video_progress"."progress" IS '영상 진행도 %';

COMMENT ON COLUMN "education"."education_video_progress"."last_position_seconds" IS '영상별 마지막 재생 위치';

COMMENT ON COLUMN "education"."education_video_progress"."total_watch_seconds" IS '이 영상의 누적 시청 시간';

COMMENT ON COLUMN "education"."education_video_progress"."is_completed" IS '완료 여부';

COMMENT ON COLUMN "education"."education_video_progress"."updated_at" IS '갱신 시각';

COMMENT ON COLUMN "education"."education_video_progress"."created_at" IS '생성 시각';

COMMENT ON COLUMN "education"."education_script"."id" IS '스크립트 버전 PK';

COMMENT ON COLUMN "education"."education_script"."education_id" IS '교육 ID';

COMMENT ON COLUMN "education"."education_script"."source_doc_id" IS '원본 문서 ID';

COMMENT ON COLUMN "education"."education_script"."version" IS '스크립트 버전 번호';

COMMENT ON COLUMN "education"."education_script"."content" IS '스크립트 내용';

COMMENT ON COLUMN "education"."education_script"."created_by" IS '스크립트 작성자 UUID';

COMMENT ON COLUMN "education"."education_script"."created_at" IS '작성 시각';

COMMENT ON COLUMN "education"."video_generation_job"."id" IS '영상 생성 작업 PK';

COMMENT ON COLUMN "education"."video_generation_job"."education_id" IS '교육 ID';

COMMENT ON COLUMN "education"."video_generation_job"."script_id" IS '사용된 스크립트 ID';

COMMENT ON COLUMN "education"."video_generation_job"."template_option" IS '영상 템플릿 옵션(JSON)';

COMMENT ON COLUMN "education"."video_generation_job"."status" IS 'PENDING/PROCESSING/SUCCESS/FAIL';

COMMENT ON COLUMN "education"."video_generation_job"."fail_reason" IS '실패 사유';

COMMENT ON COLUMN "education"."video_generation_job"."generated_video_url" IS '생성된 영상 URL';

COMMENT ON COLUMN "education"."video_generation_job"."retry_count" IS '재시도 횟수';

COMMENT ON COLUMN "education"."video_generation_job"."created_at" IS '생성 시각';

COMMENT ON COLUMN "education"."video_generation_job"."updated_at" IS '갱신 시각';

COMMENT ON COLUMN "education"."education_progress"."id" IS '학습 진행도 PK';

COMMENT ON COLUMN "education"."education_progress"."user_uuid" IS '사용자 UUID';

COMMENT ON COLUMN "education"."education_progress"."education_id" IS '교육 ID';

COMMENT ON COLUMN "education"."education_progress"."progress" IS '시청 진행도 %';

COMMENT ON COLUMN "education"."education_progress"."is_completed" IS '이수 여부';

COMMENT ON COLUMN "education"."education_progress"."completed_at" IS '완료 시각';

COMMENT ON COLUMN "education"."education_progress"."updated_at" IS '갱신 시각';

COMMENT ON COLUMN "education"."education_progress"."last_position_seconds" IS '마지막 재생 위치(초)';

COMMENT ON COLUMN "education"."education_progress"."total_watch_seconds" IS '누적 시청 시간(초)';

COMMENT ON COLUMN "education"."education_video_review"."id" IS '영상 검토 리뷰 PK';

COMMENT ON COLUMN "education"."education_video_review"."video_id" IS '검토 대상 영상 ID';

COMMENT ON COLUMN "education"."education_video_review"."reviewer_uuid" IS '검토자 UUID';

COMMENT ON COLUMN "education"."education_video_review"."status" IS '승인/반려 상태';

COMMENT ON COLUMN "education"."education_video_review"."comment" IS '검토 코멘트';

COMMENT ON COLUMN "education"."education_video_review"."created_at" IS '검토 시각';

COMMENT ON COLUMN "education"."quiz_attempt"."id" IS '퀴즈 시도 PK';

COMMENT ON COLUMN "education"."quiz_attempt"."user_uuid" IS '사용자 UUID';

COMMENT ON COLUMN "education"."quiz_attempt"."education_id" IS '교육 ID';

COMMENT ON COLUMN "education"."quiz_attempt"."score" IS '최종 점수';

COMMENT ON COLUMN "education"."quiz_attempt"."passed" IS '합격 여부';

COMMENT ON COLUMN "education"."quiz_attempt"."attempt_no" IS '시도 회차';

COMMENT ON COLUMN "education"."quiz_attempt"."created_at" IS '시작 시각';

COMMENT ON COLUMN "education"."quiz_attempt"."time_limit" IS '시간 제한(초)';

COMMENT ON COLUMN "education"."quiz_attempt"."submitted_at" IS '제출 시각';

COMMENT ON COLUMN "education"."quiz_question"."id" IS '생성된 퀴즈 문항 PK';

COMMENT ON COLUMN "education"."quiz_question"."attempt_id" IS '시도 ID';

COMMENT ON COLUMN "education"."quiz_question"."question" IS '문제 텍스트';

COMMENT ON COLUMN "education"."quiz_question"."options" IS '보기(JSON)';

COMMENT ON COLUMN "education"."quiz_question"."correct_option_idx" IS '정답 인덱스';

COMMENT ON COLUMN "education"."quiz_question"."explanation" IS '해설';

COMMENT ON COLUMN "education"."quiz_question"."user_selected_option_idx" IS '사용자 선택 보기';

COMMENT ON COLUMN "education"."quiz_leave_tracking"."id" IS '이탈 로그 PK';

COMMENT ON COLUMN "education"."quiz_leave_tracking"."attempt_id" IS '시도 ID';

COMMENT ON COLUMN "education"."quiz_leave_tracking"."leave_count" IS '탭/창 이탈 횟수';

COMMENT ON COLUMN "education"."quiz_leave_tracking"."total_leave_seconds" IS '이탈 누적 시간(초)';

COMMENT ON COLUMN "education"."quiz_leave_tracking"."last_leave_at" IS '마지막 이탈 시각';

ALTER TABLE "chat"."chat_section" ADD FOREIGN KEY ("session_id") REFERENCES "chat"."chat_session" ("id");

ALTER TABLE "chat"."chat_message" ADD FOREIGN KEY ("session_id") REFERENCES "chat"."chat_session" ("id");

ALTER TABLE "chat"."chat_message" ADD FOREIGN KEY ("section_id") REFERENCES "chat"."chat_section" ("id");

ALTER TABLE "chat"."chat_feedback" ADD FOREIGN KEY ("session_id") REFERENCES "chat"."chat_session" ("id");

ALTER TABLE "chat"."chat_feedback" ADD FOREIGN KEY ("section_id") REFERENCES "chat"."chat_section" ("id");

ALTER TABLE "chat"."chat_feedback" ADD FOREIGN KEY ("message_id") REFERENCES "chat"."chat_message" ("id");

ALTER TABLE "chat"."chat_session_feedback" ADD FOREIGN KEY ("session_id") REFERENCES "chat"."chat_session" ("id");

ALTER TABLE "education"."education_source_doc" ADD FOREIGN KEY ("education_id") REFERENCES "education"."education" ("id");

ALTER TABLE "education"."education_video" ADD FOREIGN KEY ("education_id") REFERENCES "education"."education" ("id");

ALTER TABLE "education"."education_video" ADD FOREIGN KEY ("generation_job_id") REFERENCES "education"."video_generation_job" ("id");

ALTER TABLE "education"."education_video_progress" ADD FOREIGN KEY ("education_id") REFERENCES "education"."education" ("id");

ALTER TABLE "education"."education_video_progress" ADD FOREIGN KEY ("video_id") REFERENCES "education"."education_video" ("id");

ALTER TABLE "education"."education_script" ADD FOREIGN KEY ("education_id") REFERENCES "education"."education" ("id");

ALTER TABLE "education"."education_script" ADD FOREIGN KEY ("source_doc_id") REFERENCES "education"."education_source_doc" ("id");

ALTER TABLE "education"."video_generation_job" ADD FOREIGN KEY ("education_id") REFERENCES "education"."education" ("id");

ALTER TABLE "education"."video_generation_job" ADD FOREIGN KEY ("script_id") REFERENCES "education"."education_script" ("id");

ALTER TABLE "education"."education_progress" ADD FOREIGN KEY ("education_id") REFERENCES "education"."education" ("id");

ALTER TABLE "education"."education_video_review" ADD FOREIGN KEY ("video_id") REFERENCES "education"."education_video" ("id");

ALTER TABLE "education"."quiz_question" ADD FOREIGN KEY ("attempt_id") REFERENCES "education"."quiz_attempt" ("id");

ALTER TABLE "education"."quiz_leave_tracking" ADD FOREIGN KEY ("attempt_id") REFERENCES "education"."quiz_attempt" ("id");

ALTER TABLE "infra"."rag_document_chunk" ADD FOREIGN KEY ("document_id") REFERENCES "infra"."rag_document" ("id");

ALTER TABLE "infra"."rag_fail_chunk" ADD FOREIGN KEY ("document_id") REFERENCES "infra"."rag_document" ("id");
