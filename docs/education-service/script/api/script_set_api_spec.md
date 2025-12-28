## Script Service API (문서 기준)

Base URL: http://localhost:9002

---

# 1. Script

## 1.1 스크립트 ID 조회

### ✔ URL

- GET /scripts/lookup

### ✔ 설명

- videoId 또는 educationId로 스크립트 ID를 조회합니다.
- 둘 다 제공된 경우 videoId를 우선합니다.

### ✔ 권한

`ROLE_USER`

### ✔ 요청

Body: 없음

### Query Parameter

| key         | 설명    | value 타입   | 옵션     | Nullable | 예시                                   |
| ----------- | ------- | ------------ | -------- | -------- | -------------------------------------- |
| videoId     | 영상 ID | string(uuid) | optional | true     | "550e8400-e29b-41d4-a716-446655440001" |
| educationId | 교육 ID | string(uuid) | optional | true     | "550e8400-e29b-41d4-a716-446655440000" |

### Response

| key         | 설명          | value 타입   | 옵션     | Nullable | 예시                                                  |
| ----------- | ------------- | ------------ | -------- | -------- | ----------------------------------------------------- |
| scriptId    | 스크립트 ID   | string(uuid) | required | false    | "550e8400-e29b-41d4-a716-446655440000"                |
| educationId | 교육 ID       | string(uuid) | required | false    | "550e8400-e29b-41d4-a716-446655440001"                |
| videoId     | 영상 ID       | string(uuid) | required | false    | "550e8400-e29b-41d4-a716-446655440002"                |
| title       | 스크립트 제목 | string       | required | false    | "직장내괴롭힘 교육 영상"                              |
| version     | 스크립트 버전 | number       | required | false    | 1                                                     |
| status      | 스크립트 상태 | string       | required | false    | "DRAFT" (DRAFT, REVIEW_REQUESTED, APPROVED, REJECTED) |

### Status

| status          | response content                |
| --------------- | ------------------------------- |
| 200 OK          | 정상                            |
| 400 Bad Request | videoId와 educationId 모두 없음 |
| 404 Not Found   | 스크립트를 찾을 수 없음         |
| 401/403         | 인증/권한 오류                  |

---

## 1.2 스크립트 상세 조회 (\* 개발용)

### ✔ URL

- GET /scripts/{scriptId}

### ✔ 설명

- AI가 생성한 스크립트를 조회합니다. (챕터/씬 포함)

### ✔ 권한

`ROLE_USER`

### ✔ 요청

Body: 없음

### Path

- scriptId: UUID (스크립트 ID)

### Response

| key              | 설명        | value 타입    | 옵션     | Nullable | 예시                                   |
| ---------------- | ----------- | ------------- | -------- | -------- | -------------------------------------- |
| scriptId         | 스크립트 ID | string(uuid)  | required | false    | "550e8400-e29b-41d4-a716-446655440000" |
| educationId      | 교육 ID     | string(uuid)  | required | false    | "550e8400-e29b-41d4-a716-446655440001" |
| videoId          | 영상 ID     | string(uuid)  | required | false    | "550e8400-e29b-41d4-a716-446655440002" |
| title            | 제목        | string        | required | false    | "직장내괴롭힘 교육 영상"               |
| totalDurationSec | 총 길이(초) | number        | required | false    | 720                                    |
| version          | 버전        | number        | required | false    | 1                                      |
| llmModel         | 사용 LLM    | string        | optional | true     | "gpt-4"                                |
| rawPayload       | 원본 JSON   | string        | optional | true     | "{...}"                                |
| chapters         | 챕터 목록   | array(object) | required | false    | 아래 표 참조                           |

chapters item

| key         | 설명         | value 타입    | 옵션     | Nullable | 예시                                   |
| ----------- | ------------ | ------------- | -------- | -------- | -------------------------------------- |
| chapterId   | 챕터 ID      | string(uuid)  | required | false    | "550e8400-e29b-41d4-a716-446655440003" |
| index       | 순서(0-base) | number        | required | false    | 0                                      |
| title       | 제목         | string        | required | false    | "괴롭힘"                               |
| durationSec | 길이(초)     | number        | required | false    | 180                                    |
| scenes      | 씬 목록      | array(object) | required | false    | 아래 표 참조                           |

scenes item

| key                | 설명             | value 타입    | 옵션     | Nullable | 예시                                     |
| ------------------ | ---------------- | ------------- | -------- | -------- | ---------------------------------------- |
| sceneId            | 씬 ID            | string(uuid)  | required | false    | "550e8400-e29b-41d4-a716-446655440004"   |
| index              | 챕터 내 순서     | number        | required | false    | 1                                        |
| purpose            | 목적             | string        | required | false    | "hook" (hook, concept, example, summary) |
| narration          | 내레이션         | string        | required | false    | "직장 내 괴롭힘이란..."                  |
| caption            | 자막             | string        | required | false    | "직장 내 괴롭힘이란..."                  |
| visual             | 시각 연출        | string        | required | false    | "자료 원문 문장(텍스트) 강조"            |
| durationSec        | 길이(초)         | number        | required | false    | 15                                       |
| sourceChunkIndexes | 근거 청크 인덱스 | array(number) | optional | true     | [1, 2, 3]                                |
| confidenceScore    | 신뢰도           | number        | optional | true     | 0.95                                     |

### Status

| status        | response content        |
| ------------- | ----------------------- |
| 200 OK        | 정상                    |
| 404 Not Found | 스크립트를 찾을 수 없음 |
| 401/403       | 인증/권한 오류          |

---

## 1.3 스크립트 목록 조회

### ✔ URL

- GET /scripts

### ✔ 설명

- 스크립트 목록을 페이징으로 조회합니다.

### ✔ 권한

`ROLE_USER`

### ✔ 요청

Body: 없음

### Query Parameter

| key  | 설명        | value 타입 | 옵션     | Nullable | 예시 |
| ---- | ----------- | ---------- | -------- | -------- | ---- |
| page | 페이지 번호 | number     | optional | false    | 0    |
| size | 페이지 크기 | number     | optional | false    | 10   |

### Response

array of object

| key         | 설명          | value 타입   | 옵션     | Nullable | 예시                                   |
| ----------- | ------------- | ------------ | -------- | -------- | -------------------------------------- |
| scriptId    | 스크립트 ID   | string(uuid) | required | false    | "550e8400-e29b-41d4-a716-446655440000" |
| educationId | 교육 ID       | string(uuid) | required | false    | "550e8400-e29b-41d4-a716-446655440001" |
| script      | 스크립트 내용 | string       | required | false    | "교육 영상 스크립트 내용..."           |
| version     | 스크립트 버전 | number       | required | false    | 1                                      |

### Status

| status  | response content |
| ------- | ---------------- |
| 200 OK  | 정상             |
| 401/403 | 인증/권한 오류   |

---

## 1.4 스크립트 수정

### ✔ URL

- PUT /scripts/{scriptId}

### ✔ 설명

- 관리자가 스크립트(rawPayload) 및 챕터/씬을 수정합니다.
- 챕터/씬은 전체 교체됩니다.

### ✔ 권한

`ROLE_ADMIN`

### ✔ 요청

Body: 있음

### Path

- scriptId: UUID (스크립트 ID)

### Request

| key      | 설명                                  | value 타입    | 옵션     | Nullable | 예시         |
| -------- | ------------------------------------- | ------------- | -------- | -------- | ------------ |
| script   | 수정할 스크립트 원본 JSON(rawPayload) | string        | optional | true     | "{...}"      |
| chapters | 챕터/씬 목록 (전체 교체)              | array(object) | optional | true     | 아래 표 참조 |

chapters item

| key         | 설명          | value 타입    | 옵션     | Nullable | 예시         |
| ----------- | ------------- | ------------- | -------- | -------- | ------------ |
| index       | 순서(0-base)  | number        | required | false    | 0            |
| title       | 챕터 제목     | string        | required | false    | "괴롭힘"     |
| durationSec | 챕터 길이(초) | number        | required | false    | 180          |
| scenes      | 씬 목록       | array(object) | required | false    | 아래 표 참조 |

scenes item

| key                | 설명             | value 타입    | 옵션     | Nullable | 예시                    |
| ------------------ | ---------------- | ------------- | -------- | -------- | ----------------------- |
| index              | 챕터 내 순서     | number        | required | false    | 1                       |
| purpose            | 목적             | string        | required | false    | "hook"                  |
| narration          | 내레이션         | string        | required | false    | "직장 내 괴롭힘이란..." |
| caption            | 자막             | string        | required | false    | "직장 내 괴롭힘이란..." |
| visual             | 시각 연출        | string        | required | false    | "자료 원문 문장 강조"   |
| durationSec        | 길이(초)         | number        | required | false    | 15                      |
| sourceChunkIndexes | 근거 청크 인덱스 | array(number) | optional | true     | [1, 2, 3]               |
| confidenceScore    | 신뢰도           | number        | optional | true     | 0.9                     |

### Response

| key      | 설명           | value 타입   | 옵션     | Nullable | 예시                                   |
| -------- | -------------- | ------------ | -------- | -------- | -------------------------------------- |
| updated  | 수정 성공 여부 | boolean      | required | false    | true                                   |
| scriptId | 스크립트 ID    | string(uuid) | required | false    | "550e8400-e29b-41d4-a716-446655440000" |

### Status

| status          | response content        |
| --------------- | ----------------------- |
| 200 OK          | 정상                    |
| 400 Bad Request | 잘못된 요청             |
| 404 Not Found   | 스크립트를 찾을 수 없음 |
| 401/403         | 인증/권한 오류          |

---

## 1.5 스크립트 삭제

### ✔ URL

- DELETE /scripts/{scriptId}

### ✔ 설명

- 스크립트를 삭제합니다.

### ✔ 권한

`ROLE_ADMIN`

### ✔ 요청

Body: 없음

### Path

- scriptId: UUID (스크립트 ID)

### Response

없음 (204 No Content)

### Status

| status         | response content        |
| -------------- | ----------------------- |
| 204 No Content | 삭제 성공               |
| 404 Not Found  | 스크립트를 찾을 수 없음 |
| 401/403        | 인증/권한 오류          |

---

## 1.6 스크립트 1차 승인

### ✔ URL

- POST /scripts/{scriptId}/approve

### ✔ 설명

- 스크립트를 승인합니다.
- 상태 변경: `SCRIPT_REVIEW_REQUESTED` → `SCRIPT_APPROVED`
- 영상 상태도 함께 변경됩니다.

### ✔ 권한

`ROLE_ADMIN`

### ✔ 요청

Body: 없음

### Path

- scriptId: UUID (스크립트 ID)

### Response

| key            | 설명      | value 타입   | 옵션     | Nullable | 예시                                   |
| -------------- | --------- | ------------ | -------- | -------- | -------------------------------------- |
| videoId        | 영상 ID   | string(uuid) | required | false    | "550e8400-e29b-41d4-a716-446655440001" |
| previousStatus | 이전 상태 | string       | required | false    | "SCRIPT_REVIEW_REQUESTED"              |
| currentStatus  | 현재 상태 | string       | required | false    | "SCRIPT_APPROVED"                      |
| updatedAt      | 변경 시각 | string       | required | false    | "2025-12-24T10:00:00Z"                 |

### Status

| status          | response content             |
| --------------- | ---------------------------- |
| 200 OK          | 정상                         |
| 400 Bad Request | 상태 변경 불가               |
| 404 Not Found   | 스크립트/영상을 찾을 수 없음 |
| 401/403         | 인증/권한 오류               |

---

## 1.7 스크립트 1차 반려

### ✔ URL

- POST /scripts/{scriptId}/reject

### ✔ 설명

- 스크립트를 반려합니다.
- 상태 변경: `SCRIPT_REVIEW_REQUESTED` → `SCRIPT_READY`
- 영상 상태도 함께 변경됩니다.
- 반려 사유(reason)가 제공되면 `EducationVideoReview` 테이블에 저장됩니다. (반려 단계: SCRIPT)

### ✔ 권한

`ROLE_ADMIN`

### ✔ 요청

Body: 있음 (선택)

### Path

- scriptId: UUID (스크립트 ID)

### Request

| key    | 설명      | value 타입 | 옵션     | Nullable | 예시                      |
| ------ | --------- | ---------- | -------- | -------- | ------------------------- |
| reason | 반려 사유 | string     | optional | true     | "스크립트 내용 수정 필요" |

### Response

| key            | 설명      | value 타입   | 옵션     | Nullable | 예시                                   |
| -------------- | --------- | ------------ | -------- | -------- | -------------------------------------- |
| videoId        | 영상 ID   | string(uuid) | required | false    | "550e8400-e29b-41d4-a716-446655440001" |
| previousStatus | 이전 상태 | string       | required | false    | "SCRIPT_REVIEW_REQUESTED"              |
| currentStatus  | 현재 상태 | string       | required | false    | "SCRIPT_READY"                         |
| updatedAt      | 변경 시각 | string       | required | false    | "2025-12-24T10:00:00Z"                 |

### Status

| status          | response content             |
| --------------- | ---------------------------- |
| 200 OK          | 정상                         |
| 400 Bad Request | 상태 변경 불가               |
| 404 Not Found   | 스크립트/영상을 찾을 수 없음 |
| 401/403         | 인증/권한 오류               |

---

# 2. Internal Script API (AI 서버 ↔ 백엔드)

## 2.1 스크립트 생성 완료 콜백 (내부 API)

### ✔ URL

- POST /scripts/complete

### ✔ 설명

- AI 서버가 전처리 & 스크립트 생성 완료 후 백엔드로 결과를 전달합니다.
- 스크립트를 DB에 저장하고 영상 상태를 `SCRIPT_READY`로 변경합니다.

### ✔ 권한

내부 토큰 (`X-Internal-Token`)

### ✔ 요청

Body: 있음

### Request

| key     | 설명                                   | value 타입   | 옵션     | Nullable | 예시                                   |
| ------- | -------------------------------------- | ------------ | -------- | -------- | -------------------------------------- |
| videoId | 영상 컨텐츠 ID                         | string(uuid) | required | false    | "550e8400-e29b-41d4-a716-446655440003" |
| script  | LLM이 자동 생성한 스크립트 (JSON 객체) | object       | required | false    | 아래 표 참조                           |
| version | 스크립트 버전 번호                     | number       | required | false    | 1                                      |

script object

| key                | 설명        | value 타입    | 옵션     | Nullable | 예시                     |
| ------------------ | ----------- | ------------- | -------- | -------- | ------------------------ |
| title              | 제목        | string        | required | false    | "직장내괴롭힘 교육 영상" |
| total_duration_sec | 총 길이(초) | number        | required | false    | 720                      |
| chapters           | 챕터 목록   | array(object) | required | false    | 아래 표 참조             |

chapters item

| key          | 설명      | value 타입    | 옵션     | Nullable | 예시         |
| ------------ | --------- | ------------- | -------- | -------- | ------------ |
| title        | 챕터 제목 | string        | required | false    | "괴롭힘"     |
| duration_sec | 길이(초)  | number        | required | false    | 180          |
| scenes       | 씬 목록   | array(object) | required | false    | 아래 표 참조 |

scenes item

| key           | 설명             | value 타입    | 옵션     | Nullable | 예시                    |
| ------------- | ---------------- | ------------- | -------- | -------- | ----------------------- |
| scene_id      | 씬 ID            | number        | required | false    | 1                       |
| purpose       | 목적             | string        | required | false    | "hook"                  |
| visual        | 시각 연출        | string        | required | false    | "자료 원문 문장 강조"   |
| narration     | 내레이션         | string        | required | false    | "직장 내 괴롭힘이란..." |
| caption       | 자막             | string        | required | false    | "직장 내 괴롭힘이란..." |
| duration_sec  | 길이(초)         | number        | required | false    | 15                      |
| source_chunks | 근거 청크 인덱스 | array(number) | optional | true     | [1, 2, 3]               |

### Response

| key      | 설명               | value 타입   | 옵션     | Nullable | 예시                                   |
| -------- | ------------------ | ------------ | -------- | -------- | -------------------------------------- |
| saved    | 저장 성공 여부     | boolean      | required | false    | true                                   |
| scriptId | 생성된 스크립트 ID | string(uuid) | required | false    | "550e8400-e29b-41d4-a716-446655440001" |

### Status

| status           | response content |
| ---------------- | ---------------- |
| 200 OK           | 콜백 처리 성공   |
| 400 Bad Request  | 잘못된 요청      |
| 401 Unauthorized | 내부 토큰 오류   |

---

## 2.2 스크립트 렌더 스펙 조회 (내부 API)

### ✔ URL

- GET /internal/scripts/{scriptId}/render-spec

### ✔ 설명

- AI 서버가 렌더링을 위해 스크립트의 챕터/씬 정보를 조회합니다.

### ✔ 권한

내부 토큰 (`X-Internal-Token`)

### ✔ 요청

Body: 없음

### Path

- scriptId: UUID (스크립트 ID)

### Response

| key                | 설명             | value 타입    | 옵션     | Nullable | 예시                                   |
| ------------------ | ---------------- | ------------- | -------- | -------- | -------------------------------------- |
| script_id          | 스크립트 ID      | string        | required | false    | "550e8400-e29b-41d4-a716-446655440000" |
| video_id           | 영상 ID          | string        | required | false    | "550e8400-e29b-41d4-a716-446655440001" |
| title              | 영상 제목        | string        | required | false    | "직장내괴롭힘 교육 영상"               |
| total_duration_sec | 총 영상 길이(초) | number        | required | false    | 720                                    |
| scenes             | 씬 목록          | array(object) | required | false    | 아래 표 참조                           |

scenes item

| key           | 설명                 | value 타입 | 옵션     | Nullable | 예시                                   |
| ------------- | -------------------- | ---------- | -------- | -------- | -------------------------------------- |
| scene_id      | 씬 ID                | string     | required | false    | "550e8400-e29b-41d4-a716-446655440004" |
| scene_order   | 씬 순서 (1부터 시작) | number     | required | false    | 1                                      |
| chapter_title | 챕터 제목            | string     | required | false    | "괴롭힘"                               |
| purpose       | 씬 목적              | string     | required | false    | "hook"                                 |
| narration     | 나레이션 텍스트      | string     | required | false    | "직장 내 괴롭힘이란..."                |
| caption       | 화면 캡션            | string     | required | false    | "직장 내 괴롭힘이란..."                |
| duration_sec  | 씬 지속 시간(초)     | number     | required | false    | 15                                     |
| visual_spec   | 시각 사양            | object     | required | false    | 아래 표 참조                           |

visual_spec object

| key             | 설명           | value 타입    | 옵션     | Nullable | 예시               |
| --------------- | -------------- | ------------- | -------- | -------- | ------------------ |
| type            | 시각 타입      | string        | required | false    | "TEXT_HIGHLIGHT"   |
| text            | 표시 텍스트    | string        | required | false    | "직장 내 괴롭힘"   |
| highlight_terms | 강조 용어 목록 | array(string) | optional | true     | ["괴롭힘", "직장"] |

### Status

| status           | response content        |
| ---------------- | ----------------------- |
| 200 OK           | 조회 성공               |
| 401 Unauthorized | 내부 토큰 오류          |
| 404 Not Found    | 스크립트를 찾을 수 없음 |

---

## 주의사항

1. **스크립트 상태 전이**: 스크립트 상태는 다음 순서로 전이됩니다:
   - 생성: `SCRIPT_READY` → 검토 요청: `SCRIPT_REVIEW_REQUESTED` → 승인: `SCRIPT_APPROVED` 또는 반려: `SCRIPT_READY`
2. **스크립트 수정**: 챕터/씬을 수정할 때는 전체를 교체해야 합니다. 부분 수정은 지원하지 않습니다.
3. **스크립트 생성**: AI 서버가 스크립트 생성 완료 후 콜백을 통해 백엔드로 전달합니다.
4. **렌더 스펙**: AI 서버가 영상 렌더링을 위해 스크립트의 씬 정보를 조회할 때 사용합니다.
5. **내부 API**: `/scripts/complete`와 `/internal/scripts/{scriptId}/render-spec` 엔드포인트는 내부 토큰(`X-Internal-Token`)이 필요하며, 외부에서 직접 호출하지 않습니다.

---

## 관련 문서

- [Video API 명세](../video/api/video_api_spec.md)
- [SourceSet API 명세](../source-set/api/source_set_api_spec.md)
- [Video Creation Workflow](../video/flow/video_creation_workflow.md)
