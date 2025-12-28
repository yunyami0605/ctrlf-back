## SourceSet Service API (문서 기준)

Base URL: http://localhost:9002

---

# 1. SourceSet

## 1.1 소스셋 생성(문서 묶기)

### ✔ URL

- POST /video/source-sets

### ✔ 설명

- 여러 문서를 하나의 영상 제작 단위(SourceSet)로 묶습니다.
- 소스셋 생성 후 AI 서버에 스크립트 생성 작업을 자동으로 요청합니다.
- 트랜잭션 커밋 후 AI 서버에 `/v2.1/source-sets/{sourceSetId}/start` 호출합니다.

### ✔ 권한

`ROLE_USER`

### ✔ 요청

Body: 있음

### Request

| key         | 설명              | value 타입      | 옵션     | Nullable | 예시                                    |
| ----------- | ----------------- | --------------- | -------- | -------- | --------------------------------------- |
| title       | 소스셋 제목       | string          | required | false    | "직장내괴롭힘 통합 교육자료"            |
| domain      | 소스셋 도메인     | string          | optional | true     | "FOUR_MANDATORY"                        |
| documentIds | 포함할 문서 ID 목록 | array(string)   | required | false    | ["doc-123", "doc-456"]                  |
| educationId | 연결된 교육 ID    | string(uuid)    | required | false    | "550e8400-e29b-41d4-a716-446655440000"  |
| videoId     | 연결된 영상 ID    | string(uuid)    | required | false    | "550e8400-e29b-41d4-a716-446655440001" |

### Query Parameter

없음

### Response

| key         | 설명              | value 타입      | 옵션     | Nullable | 예시                    |
| ----------- | ----------------- | --------------- | -------- | -------- | ----------------------- |
| sourceSetId | 소스셋 ID         | string(uuid)    | required | false    | "550e8400-e29b-..."     |
| status      | 상태              | string          | required | false    | "CREATED"               |
| documentIds | 포함된 문서 ID 목록 | array(string)   | required | false    | ["doc-123", "doc-456"]  |

### Status

| status          | response content |
| --------------- | ---------------- |
| 201 Created     | 정상 생성        |
| 400 Bad Request | 유효성 실패      |
| 403 Forbidden   | 권한 없음        |
| 404 Not Found   | 교육/영상을 찾을 수 없음 |
| 500 Internal Server Error | 생성 실패 |

---

## 1.2 소스셋 문서 변경(추가/제거)

### ✔ URL

- PATCH /video/source-sets/{sourceSetId}

### ✔ 설명

- 소스셋의 문서 목록을 수정합니다.
- 문서를 추가하거나 제거할 수 있습니다.
- 단, LOCKED 상태 이후는 변경이 금지됩니다.

### ✔ 권한

`ROLE_USER`

### ✔ 요청

Body: 있음

### Path

- sourceSetId: UUID (소스셋 ID)

### Request

| key             | 설명          | value 타입      | 옵션     | Nullable | 예시                |
| --------------- | ------------- | --------------- | -------- | -------- | ------------------- |
| addDocumentIds  | 추가할 문서 IDs | array(string)   | optional | true     | ["doc-789"]         |
| removeDocumentIds | 제거할 문서 IDs | array(string)   | optional | true     | ["doc-123"]         |
| comment         | 변경 사유     | string          | optional | true     | "문서 업데이트"      |

### Response

| key         | 설명              | value 타입      | 옵션     | Nullable | 예시                    |
| ----------- | ----------------- | --------------- | -------- | -------- | ----------------------- |
| sourceSetId | 소스셋 ID         | string(uuid)    | required | false    | "550e8400-e29b-..."     |
| status      | 상태              | string          | required | false    | "CREATED"               |
| documentIds | 포함된 문서 ID 목록 | array(string)   | required | false    | ["doc-456", "doc-789"]  |

### Status

| status          | response content |
| --------------- | ---------------- |
| 200 OK          | 정상 변경        |
| 400 Bad Request | 유효성 실패      |
| 403 Forbidden   | 권한 없음        |
| 404 Not Found   | 소스셋/문서 없음 |
| 409 Conflict    | LOCKED 상태(변경 불가) |
| 500 Internal Server Error | 변경 실패 |

---

# 2. Internal SourceSet API (AI 서버 ↔ 백엔드)

## 2.1 소스셋 문서 목록 조회 (내부 API)

### ✔ URL

- GET /internal/source-sets/{sourceSetId}/documents

### ✔ 설명

- FastAPI가 sourceSet에 포함된 RagDocument 목록을 조회합니다.
- 각 문서의 상세 정보(제목, 도메인, 원본 파일 URL, 상태)를 포함합니다.

### ✔ 권한

내부 토큰 (`X-Internal-Token`)

### ✔ 요청

Body: 없음

### Path

- sourceSetId: UUID (소스셋 ID)

### Query Parameter

없음

### Response

| key       | 설명      | value 타입      | 옵션     | Nullable | 예시         |
| --------- | --------- | --------------- | -------- | -------- | ------------ |
| sourceSetId | 소스셋 ID | string(uuid)    | required | false    | "550e..."    |
| documents | 문서 목록 | array(object)   | required | false    | 아래 표 참조 |

documents item

| key        | 설명          | value 타입 | 옵션     | Nullable | 예시                                    |
| ---------- | ------------- | ---------- | -------- | -------- | --------------------------------------- |
| documentId | 문서 ID       | string     | required | false    | "doc-123"                               |
| title      | 제목          | string     | required | false    | "산업안전 규정집 v3"                    |
| domain     | 도메인        | string     | required | false    | "HR"                                    |
| sourceUrl  | 원본 파일 URL | string     | required | false    | "s3://ctrl-s3/docs/hr_safety_v3.pdf"    |
| status     | 상태          | string     | required | false    | "COMPLETED" (QUEUED, PROCESSING, COMPLETED, FAILED) |

### Status

| status          | response content |
| --------------- | ---------------- |
| 200 OK          | 정상             |
| 401 Unauthorized | 내부 토큰 오류   |
| 404 Not Found   | 소스셋을 찾을 수 없음 |

---

## 2.2 소스셋 완료 콜백 (내부 API)

### ✔ URL

- POST /internal/callbacks/source-sets/{sourceSetId}/complete

### ✔ 설명

- sourceSet 오케스트레이션 완료 결과를 Spring에 전달합니다 (성공/실패).
- AI 서버가 스크립트 생성 완료 후 호출합니다.
- 성공 시 스크립트를 DB에 저장하고 영상 상태를 `SCRIPT_READY`로 변경합니다.

### ✔ 권한

내부 토큰 (`X-Internal-Token`)

### ✔ 요청

Body: 있음

### Path

- sourceSetId: UUID (소스셋 ID)

### Request

| key            | 설명                    | value 타입      | 옵션     | Nullable | 예시                                    |
| -------------- | ----------------------- | --------------- | -------- | -------- | --------------------------------------- |
| videoId        | 영상 ID                 | string(uuid)    | required | false    | "550e8400-e29b-41d4-a716-446655440001"  |
| status         | 결과 상태                | string          | required | false    | "COMPLETED" 또는 "FAILED"                |
| sourceSetStatus | DB source_set 상태      | string          | required | false    | "SCRIPT_READY" 또는 "FAILED"            |
| documents      | 문서별 결과              | array(object)   | required | false    | 아래 표 참조                             |
| script         | 생성된 스크립트 (성공 시) | object          | optional | true     | 아래 표 참조                             |
| errorCode      | 실패 코드                | string          | optional | true     | "DOCUMENT_PROCESSING_FAILED"             |
| errorMessage   | 실패 메시지              | string          | optional | true     | "문서 처리 중 오류 발생"                  |
| requestId      | 멱등 키                  | string(uuid)    | optional | true     | "550e8400-e29b-41d4-a716-446655440002"  |
| traceId        | 추적용                   | string          | optional | true     | "trace-550e8400..."                     |

documents item

| key        | 설명                    | value 타입 | 옵션     | Nullable | 예시                        |
| ---------- | ----------------------- | ---------- | -------- | -------- | --------------------------- |
| documentId | 문서 ID                 | string     | required | false    | "doc-123"                   |
| status     | 상태                    | string     | required | false    | "COMPLETED" 또는 "FAILED"   |
| failReason | 실패 사유 (실패 시)     | string     | optional | true     | "파일 형식 오류"            |

script (성공 시)

| key             | 설명          | value 타입      | 옵션     | Nullable | 예시                                    |
| --------------- | ------------- | --------------- | -------- | -------- | --------------------------------------- |
| scriptId        | 스크립트 ID (FastAPI 생성) | string(uuid)    | optional | true     | "550e8400-e29b-41d4-a716-446655440003"  |
| educationId     | 교육 ID       | string(uuid)    | required | false    | "550e8400-e29b-41d4-a716-446655440000"  |
| sourceSetId     | 소스셋 ID     | string(uuid)    | required | false    | "550e8400-e29b-41d4-a716-446655440004"  |
| title           | 제목          | string          | required | false    | "직장내괴롭힘 교육 영상"                |
| totalDurationSec | 총 길이(초)   | number          | required | false    | 720                                     |
| version         | 버전          | number          | required | false    | 1                                       |
| llmModel        | LLM 모델      | string          | optional | true     | "gpt-4"                                 |
| chapters        | 챕터 목록     | array(object)   | required | false    | 아래 표 참조                             |

chapters item

| key         | 설명      | value 타입      | 옵션     | Nullable | 예시         |
| ----------- | --------- | --------------- | -------- | -------- | ------------ |
| chapterIndex | 챕터 인덱스 | number        | required | false    | 0            |
| title        | 제목      | string          | required | false    | "괴롭힘"     |
| durationSec  | 길이(초)  | number          | required | false    | 180          |
| scenes       | 씬 목록    | array(object)   | required | false    | 아래 표 참조 |

scenes item

| key            | 설명          | value 타입      | 옵션     | Nullable | 예시                                    |
| -------------- | ------------- | --------------- | -------- | -------- | --------------------------------------- |
| sceneIndex     | 씬 인덱스      | number          | required | false    | 1                                       |
| purpose        | 목적          | string          | required | false    | "hook"                                  |
| narration      | 내레이션      | string          | required | false    | "직장 내 괴롭힘이란..."                 |
| caption        | 자막          | string          | required | false    | "직장 내 괴롭힘이란..."                 |
| visual         | 시각 연출      | string          | required | false    | "자료 원문 문장(텍스트) 강조"           |
| durationSec    | 길이(초)      | number          | required | false    | 15                                      |
| confidenceScore | 신뢰도        | number          | optional | true     | 0.95                                    |
| sourceRefs     | 출처 참조 (멀티문서) | array(object) | optional | true     | 아래 표 참조                             |

sourceRefs item

| key        | 설명      | value 타입 | 옵션     | Nullable | 예시      |
| ---------- | --------- | ---------- | -------- | -------- | --------- |
| documentId | 문서 ID   | string     | required | false    | "doc-123" |
| chunkIndex | 청크 인덱스 | number   | required | false    | 0         |

### Response

| key      | 설명                    | value 타입   | 옵션     | Nullable | 예시                                    |
| -------- | ----------------------- | ------------ | -------- | -------- | --------------------------------------- |
| saved    | 저장 성공 여부          | boolean      | required | false    | true                                    |
| scriptId | 생성된 스크립트 ID (저장 성공 시) | string(uuid) | optional | true     | "550e8400-e29b-41d4-a716-446655440003"  |

### Status

| status          | response content |
| --------------- | ---------------- |
| 200 OK          | 콜백 처리 성공   |
| 400 Bad Request | 잘못된 요청      |
| 401 Unauthorized | 내부 토큰 오류   |
| 404 Not Found   | 소스셋을 찾을 수 없음 (정상 처리, 경고 로그만 기록) |

---

## 2.3 S3 Presigned 다운로드 URL 조회 (내부 API)

### ✔ URL

- POST /internal/s3/download

### ✔ 설명

- FastAPI가 S3 URL을 presigned URL로 변환하기 위해 호출합니다.
- education-service가 infra-service의 `/infra/files/presign/download` API를 호출하여 presigned URL을 생성합니다.

### ✔ 권한

내부 토큰 (`X-Internal-Token`)

### ✔ 요청

Body: 있음

### Request

| key     | 설명        | value 타입 | 옵션     | Nullable | 예시                                    |
| ------- | ----------- | ---------- | -------- | -------- | --------------------------------------- |
| fileUrl | S3 파일 URL | string     | required | false    | "s3://ctrl-s3/docs/hr_safety_v3.pdf"    |

### Query Parameter

없음

### Response

| key         | 설명                  | value 타입 | 옵션     | Nullable | 예시                                    |
| ----------- | --------------------- | ---------- | -------- | -------- | --------------------------------------- |
| downloadUrl | Presigned 다운로드 URL | string     | required | false    | "https://ctrl-s3.s3.ap-northeast-2.amazonaws.com/docs/hr_safety_v3.pdf?X-Amz-Algorithm=..." |

### Status

| status          | response content |
| --------------- | ---------------- |
| 200 OK          | 조회 성공        |
| 400 Bad Request | 잘못된 요청      |
| 401 Unauthorized | 내부 토큰 오류   |
| 500 Internal Server Error | Infra service 호출 실패 |

---

## 주의사항

1. **소스셋 생성**: 소스셋 생성 후 AI 서버에 스크립트 생성 작업이 자동으로 요청됩니다. 이는 비동기로 처리되며, 완료까지 시간이 걸릴 수 있습니다.
2. **상태 변경**: 소스셋 상태는 `CREATED` → `PROCESSING` → `SCRIPT_READY` 또는 `FAILED`로 변경됩니다.
3. **문서 변경 제한**: LOCKED 상태 이후에는 문서 변경이 불가능합니다.
4. **내부 API**: `/internal/*` 엔드포인트는 내부 토큰(`X-Internal-Token`)이 필요하며, 외부에서 직접 호출하지 않습니다.
5. **콜백 처리**: 소스셋이 이미 삭제된 경우 404 응답이 반환되지만, 정상 처리되며 경고 로그만 기록됩니다.

---

## 관련 문서

- [Video API 명세](../video/api/video_api_spec.md)
- [Script API 명세](../script/api/script_api_spec.md)
- [Video Creation Workflow](../video/flow/video_creation_workflow.md)

