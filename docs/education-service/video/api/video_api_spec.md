## Video Service API (문서 기준)

Base URL: http://localhost:9002

---

# 1. Video (Admin)

## 1.1 영상 컨텐츠 생성

### ✔ URL

- POST /admin/videos

### ✔ 설명

- DRAFT 상태의 새 교육 영상 컨텐츠를 생성합니다.

### ✔ 권한

`ROLE_ADMIN`

### ✔ 요청

Body: 있음

### Request

| key             | 설명                | value 타입    | 옵션     | Nullable | 예시                                   |
| --------------- | ------------------- | ------------- | -------- | -------- | -------------------------------------- |
| educationId     | 교육 ID             | string(uuid)  | required | false    | "550e8400-e29b-41d4-a716-446655440000" |
| title           | 영상 제목           | string        | required | false    | "2024년 성희롱 예방 교육"              |
| departmentScope | 수강 가능 부서 목록 | array(string) | optional | true     | ["HR", "ENGINEERING"]                  |

### Query Parameter

없음

### Response

| key     | 설명           | value 타입   | 옵션     | Nullable | 예시                                   |
| ------- | -------------- | ------------ | -------- | -------- | -------------------------------------- |
| videoId | 생성된 영상 ID | string(uuid) | required | false    | "550e8400-e29b-41d4-a716-446655440001" |
| status  | 상태           | string       | required | false    | "DRAFT"                                |

### Status

| status          | response content |
| --------------- | ---------------- |
| 201 Created     | 정상 생성        |
| 400 Bad Request | 유효성 실패      |
| 401/403         | 인증/권한 오류   |

---

## 1.2 영상 목록 조회(페이징)

### ✔ URL

- GET /admin/videos/list

### ✔ 설명

- 영상 메타 목록을 페이징으로 조회합니다.

### ✔ 권한

`ROLE_ADMIN`

### ✔ 요청

Body: 없음

### Query Parameter

| key  | 설명        | value 타입 | 옵션     | Nullable | 예시 |
| ---- | ----------- | ---------- | -------- | -------- | ---- |
| page | 페이지 번호 | number     | optional | false    | 0    |
| size | 페이지 크기 | number     | optional | false    | 10   |

### Response

array of object

| key             | 설명                | value 타입    | 옵션     | Nullable | 예시                                   |
| --------------- | ------------------- | ------------- | -------- | -------- | -------------------------------------- |
| id              | 영상 ID             | string(uuid)  | required | false    | "550e8400-e29b-41d4-a716-446655440001" |
| educationId     | 교육 ID             | string(uuid)  | required | false    | "550e8400-e29b-41d4-a716-446655440000" |
| title           | 영상 제목           | string        | required | false    | "2024년 성희롱 예방 교육"              |
| generationJobId | 생성 Job ID         | string(uuid)  | optional | true     | "550e8400-e29b-41d4-a716-446655440002" |
| scriptId        | 스크립트 ID         | string(uuid)  | optional | true     | "550e8400-e29b-41d4-a716-446655440003" |
| fileUrl         | 파일 URL            | string        | optional | true     | "https://cdn.com/video.mp4"            |
| version         | 버전                | number        | optional | true     | 1                                      |
| duration        | 길이(초)            | number        | optional | true     | 720                                    |
| status          | 상태                | string        | required | false    | "DRAFT"                                |
| departmentScope | 수강 가능 부서 목록 | array(string) | optional | true     | ["HR", "ENGINEERING"]                  |
| orderIndex      | 재생 순서(0-base)   | number        | optional | true     | 0                                      |
| createdAt       | 생성시각 ISO8601    | string        | required | false    | "2025-12-24T10:00:00Z"                 |

### Status

| status  | response content |
| ------- | ---------------- |
| 200 OK  | 정상             |
| 401/403 | 인증/권한 오류   |

---

## 1.3 영상 상세 조회

### ✔ URL

- GET /video/{videoId}

### ✔ 설명

- 영상 메타 정보를 조회합니다.

### ✔ 권한

`ROLE_USER`

### ✔ 요청

Body: 없음

### Path

- videoId: UUID (영상 ID)

### Response

| key             | 설명                | value 타입    | 옵션     | Nullable | 예시                                   |
| --------------- | ------------------- | ------------- | -------- | -------- | -------------------------------------- |
| id              | 영상 ID             | string(uuid)  | required | false    | "550e8400-e29b-41d4-a716-446655440001" |
| educationId     | 교육 ID             | string(uuid)  | required | false    | "550e8400-e29b-41d4-a716-446655440000" |
| title           | 영상 제목           | string        | required | false    | "2024년 성희롱 예방 교육"              |
| generationJobId | 생성 Job ID         | string(uuid)  | optional | true     | "550e8400-e29b-41d4-a716-446655440002" |
| scriptId        | 스크립트 ID         | string(uuid)  | optional | true     | "550e8400-e29b-41d4-a716-446655440003" |
| fileUrl         | 파일 URL            | string        | optional | true     | "https://cdn.com/video.mp4"            |
| version         | 버전                | number        | optional | true     | 1                                      |
| duration        | 길이(초)            | number        | optional | true     | 720                                    |
| status          | 상태                | string        | required | false    | "DRAFT"                                |
| departmentScope | 수강 가능 부서 목록 | array(string) | optional | true     | ["HR", "ENGINEERING"]                  |
| orderIndex      | 재생 순서(0-base)   | number        | optional | true     | 0                                      |
| createdAt       | 생성시각 ISO8601    | string        | required | false    | "2025-12-24T10:00:00Z"                 |

### Status

| status        | response content    |
| ------------- | ------------------- |
| 200 OK        | 정상                |
| 404 Not Found | 영상을 찾을 수 없음 |
| 401/403       | 인증/권한 오류      |

---

## 1.4 영상 수정

### ✔ URL

- PUT /admin/videos/{videoId}

### ✔ 설명

- 제목/파일 URL/버전/길이/상태/부서코드 등을 부분 업데이트합니다.

### ✔ 권한

`ROLE_ADMIN`

### ✔ 요청

Body: 있음

### Path

- videoId: UUID (영상 ID)

### Request

| key             | 설명                | value 타입    | 옵션     | Nullable | 예시                             |
| --------------- | ------------------- | ------------- | -------- | -------- | -------------------------------- |
| title           | 영상 제목           | string        | optional | true     | "2024년 성희롱 예방 교육 (수정)" |
| fileUrl         | 파일 URL            | string        | optional | true     | "https://cdn.com/video.mp4"      |
| version         | 버전                | number        | optional | true     | 2                                |
| duration        | 길이(초)            | number        | optional | true     | 750                              |
| status          | 상태                | string        | optional | true     | "READY"                          |
| departmentScope | 수강 가능 부서 목록 | array(string) | optional | true     | ["HR", "ENGINEERING"]            |
| orderIndex      | 재생 순서(0-base)   | number        | optional | true     | 1                                |

### Response

| key             | 설명                | value 타입    | 옵션     | Nullable | 예시                                   |
| --------------- | ------------------- | ------------- | -------- | -------- | -------------------------------------- |
| id              | 영상 ID             | string(uuid)  | required | false    | "550e8400-e29b-41d4-a716-446655440001" |
| educationId     | 교육 ID             | string(uuid)  | required | false    | "550e8400-e29b-41d4-a716-446655440000" |
| title           | 영상 제목           | string        | required | false    | "2024년 성희롱 예방 교육 (수정)"       |
| generationJobId | 생성 Job ID         | string(uuid)  | optional | true     | "550e8400-e29b-41d4-a716-446655440002" |
| scriptId        | 스크립트 ID         | string(uuid)  | optional | true     | "550e8400-e29b-41d4-a716-446655440003" |
| fileUrl         | 파일 URL            | string        | optional | true     | "https://cdn.com/video.mp4"            |
| version         | 버전                | number        | optional | true     | 2                                      |
| duration        | 길이(초)            | number        | optional | true     | 750                                    |
| status          | 상태                | string        | required | false    | "READY"                                |
| departmentScope | 수강 가능 부서 목록 | array(string) | optional | true     | ["HR", "ENGINEERING"]                  |
| orderIndex      | 재생 순서(0-base)   | number        | optional | true     | 1                                      |
| createdAt       | 생성시각 ISO8601    | string        | required | false    | "2025-12-24T10:00:00Z"                 |

### Status

| status        | response content    |
| ------------- | ------------------- |
| 200 OK        | 정상                |
| 404 Not Found | 영상을 찾을 수 없음 |
| 401/403       | 인증/권한 오류      |

---

## 1.5 영상 삭제

### ✔ URL

- DELETE /admin/videos/{videoId}

### ✔ 설명

- 영상을 삭제합니다.

### ✔ 권한

`ROLE_ADMIN`

### ✔ 요청

Body: 없음

### Path

- videoId: UUID (영상 ID)

### Response

없음 (204 No Content)

### Status

| status         | response content    |
| -------------- | ------------------- |
| 204 No Content | 삭제 성공           |
| 404 Not Found  | 영상을 찾을 수 없음 |
| 401/403        | 인증/권한 오류      |

---

## 1.6 검토 요청

### ✔ URL

- PUT /admin/videos/{videoId}/review-request

### ✔ 설명

- 영상 생성 완료 후 검토자에게 검토를 요청합니다.
- 상태 변경:
  - 1차 검토 요청: `SCRIPT_READY` → `SCRIPT_REVIEW_REQUESTED` (스크립트 검토)
  - 2차 검토 요청: `READY` → `FINAL_REVIEW_REQUESTED` (영상 검토)

### ✔ 권한

`ROLE_ADMIN`

### ✔ 요청

Body: 없음

### Path

- videoId: UUID (영상 ID)

### Response

| key            | 설명      | value 타입   | 옵션     | Nullable | 예시                                                    |
| -------------- | --------- | ------------ | -------- | -------- | ------------------------------------------------------- |
| videoId        | 영상 ID   | string(uuid) | required | false    | "550e8400-e29b-41d4-a716-446655440001"                  |
| previousStatus | 이전 상태 | string       | required | false    | "SCRIPT_READY" 또는 "READY"                             |
| currentStatus  | 현재 상태 | string       | required | false    | "SCRIPT_REVIEW_REQUESTED" 또는 "FINAL_REVIEW_REQUESTED" |
| updatedAt      | 변경 시각 | string       | required | false    | "2025-12-24T10:00:00Z"                                  |

### Status

| status          | response content    |
| --------------- | ------------------- |
| 200 OK          | 정상                |
| 400 Bad Request | 상태 변경 불가      |
| 404 Not Found   | 영상을 찾을 수 없음 |
| 401/403         | 인증/권한 오류      |

---

## 1.7 검토 승인

### ✔ URL

- PUT /admin/videos/{videoId}/approve

### ✔ 설명

- 검토자가 영상을 승인합니다.
- 상태 변경:
  - 1차 승인: `SCRIPT_REVIEW_REQUESTED` → `SCRIPT_APPROVED` (스크립트 승인, 영상 생성 가능)
  - 2차 승인: `FINAL_REVIEW_REQUESTED` → `PUBLISHED` (영상 승인 = 게시)

### ✔ 권한

`ROLE_ADMIN`

### ✔ 요청

Body: 없음

### Path

- videoId: UUID (영상 ID)

### Response

| key            | 설명      | value 타입   | 옵션     | Nullable | 예시                                                    |
| -------------- | --------- | ------------ | -------- | -------- | ------------------------------------------------------- |
| videoId        | 영상 ID   | string(uuid) | required | false    | "550e8400-e29b-41d4-a716-446655440001"                  |
| previousStatus | 이전 상태 | string       | required | false    | "SCRIPT_REVIEW_REQUESTED" 또는 "FINAL_REVIEW_REQUESTED" |
| currentStatus  | 현재 상태 | string       | required | false    | "SCRIPT_APPROVED" 또는 "PUBLISHED"                      |
| updatedAt      | 변경 시각 | string       | required | false    | "2025-12-24T10:00:00Z"                                  |

### Status

| status          | response content    |
| --------------- | ------------------- |
| 200 OK          | 정상                |
| 400 Bad Request | 상태 변경 불가      |
| 404 Not Found   | 영상을 찾을 수 없음 |
| 401/403         | 인증/권한 오류      |

---

## 1.8 검토 반려

### ✔ URL

- PUT /admin/videos/{videoId}/reject

### ✔ 설명

- 검토자가 영상을 반려합니다.
- 상태 변경:
  - 1차 반려: `SCRIPT_REVIEW_REQUESTED` → `SCRIPT_READY` (스크립트 검토 단계 반려)
  - 2차 반려: `FINAL_REVIEW_REQUESTED` → `READY` (영상 검토 단계 반려)
- 반려 사유(reason)가 제공되면 `EducationVideoReview` 테이블에 저장됩니다. (반려 단계: SCRIPT 또는 VIDEO)

### ✔ 권한

`ROLE_ADMIN`

### ✔ 요청

Body: 있음 (선택)

### Path

- videoId: UUID (영상 ID)

### Request

| key    | 설명      | value 타입 | 옵션     | Nullable | 예시                      |
| ------ | --------- | ---------- | -------- | -------- | ------------------------- |
| reason | 반려 사유 | string     | optional | true     | "스크립트 내용 수정 필요" |

### Response

| key            | 설명      | value 타입   | 옵션     | Nullable | 예시                                                    |
| -------------- | --------- | ------------ | -------- | -------- | ------------------------------------------------------- |
| videoId        | 영상 ID   | string(uuid) | required | false    | "550e8400-e29b-41d4-a716-446655440001"                  |
| previousStatus | 이전 상태 | string       | required | false    | "SCRIPT_REVIEW_REQUESTED" 또는 "FINAL_REVIEW_REQUESTED" |
| currentStatus  | 현재 상태 | string       | required | false    | "SCRIPT_READY" 또는 "READY"                             |
| updatedAt      | 변경 시각 | string       | required | false    | "2025-12-24T10:00:00Z"                                  |

### Status

| status          | response content    |
| --------------- | ------------------- |
| 200 OK          | 정상                |
| 400 Bad Request | 상태 변경 불가      |
| 404 Not Found   | 영상을 찾을 수 없음 |
| 401/403         | 인증/권한 오류      |

---

## 1.9 게시

### ✔ URL

- PUT /admin/videos/{videoId}/publish

### ✔ 설명

- [Deprecated] 게시는 2차 승인(approve) 시 자동으로 `PUBLISHED` 처리됩니다.
- 기존 API 호환을 위해 유지하되, `PUBLISHED` 상태가 아닌 경우 에러를 반환합니다.
- 상태 변경: `APPROVED` → `ACTIVE` (사용하지 않음, `approve`에서 자동 처리)

### ✔ 권한

`ROLE_ADMIN`

### ✔ 요청

Body: 없음

### Path

- videoId: UUID (영상 ID)

### Response

| key            | 설명      | value 타입   | 옵션     | Nullable | 예시                                                         |
| -------------- | --------- | ------------ | -------- | -------- | ------------------------------------------------------------ |
| videoId        | 영상 ID   | string(uuid) | required | false    | "550e8400-e29b-41d4-a716-446655440001"                       |
| previousStatus | 이전 상태 | string       | required | false    | "PUBLISHED" (Deprecated API, 실제로는 approve에서 자동 처리) |
| currentStatus  | 현재 상태 | string       | required | false    | "PUBLISHED" (Deprecated API, 실제로는 approve에서 자동 처리) |
| updatedAt      | 변경 시각 | string       | required | false    | "2025-12-24T10:00:00Z"                                       |

### Status

| status          | response content    |
| --------------- | ------------------- |
| 200 OK          | 정상                |
| 400 Bad Request | 상태 변경 불가      |
| 404 Not Found   | 영상을 찾을 수 없음 |
| 401/403         | 인증/권한 오류      |

---

## 1.10 영상 비활성화

### ✔ URL

- PUT /admin/videos/{videoId}/disable

### ✔ 설명

- 게시된 영상을 비활성화하여 유저에게 노출되지 않도록 합니다.
- 상태 변경: `PUBLISHED` → `DISABLED`

### ✔ 권한

`ROLE_ADMIN`

### ✔ 요청

Body: 없음

### Path

- videoId: UUID (영상 ID)

### Response

| key            | 설명      | value 타입   | 옵션     | Nullable | 예시                                   |
| -------------- | --------- | ------------ | -------- | -------- | -------------------------------------- |
| videoId        | 영상 ID   | string(uuid) | required | false    | "550e8400-e29b-41d4-a716-446655440001" |
| previousStatus | 이전 상태 | string       | required | false    | "PUBLISHED"                            |
| currentStatus  | 현재 상태 | string       | required | false    | "DISABLED"                             |
| updatedAt      | 변경 시각 | string       | required | false    | "2025-12-24T10:00:00Z"                 |

### Status

| status          | response content                           |
| --------------- | ------------------------------------------ |
| 200 OK          | 정상                                       |
| 400 Bad Request | 상태 변경 불가 (PUBLISHED 상태에서만 가능) |
| 404 Not Found   | 영상을 찾을 수 없음                        |
| 401/403         | 인증/권한 오류                             |

---

## 1.11 영상 활성화

### ✔ URL

- PUT /admin/videos/{videoId}/enable

### ✔ 설명

- 비활성화된 영상을 다시 활성화하여 유저에게 노출합니다.
- 상태 변경: `DISABLED` → `PUBLISHED`

### ✔ 권한

`ROLE_ADMIN`

### ✔ 요청

Body: 없음

### Path

- videoId: UUID (영상 ID)

### Response

| key            | 설명      | value 타입   | 옵션     | Nullable | 예시                                   |
| -------------- | --------- | ------------ | -------- | -------- | -------------------------------------- |
| videoId        | 영상 ID   | string(uuid) | required | false    | "550e8400-e29b-41d4-a716-446655440001" |
| previousStatus | 이전 상태 | string       | required | false    | "DISABLED"                             |
| currentStatus  | 현재 상태 | string       | required | false    | "PUBLISHED"                            |
| updatedAt      | 변경 시각 | string       | required | false    | "2025-12-24T10:00:00Z"                 |

### Status

| status          | response content                          |
| --------------- | ----------------------------------------- |
| 200 OK          | 정상                                      |
| 400 Bad Request | 상태 변경 불가 (DISABLED 상태에서만 가능) |
| 404 Not Found   | 영상을 찾을 수 없음                       |
| 401/403         | 인증/권한 오류                            |

---

## 1.12 영상 상태 강제 변경 (\* 개발용)

### ✔ URL

- PUT /admin/videos/{videoId}/status

### ✔ 설명

- 어드민 테스트용: 영상 상태를 강제로 변경합니다. (상태 검증 없음)

### ✔ 권한

`ROLE_ADMIN`

### ✔ 요청

Body: 없음

### Path

- videoId: UUID (영상 ID)

### Query Parameter

| key    | 설명        | value 타입 | 옵션     | Nullable | 예시    |
| ------ | ----------- | ---------- | -------- | -------- | ------- |
| status | 변경할 상태 | string     | required | false    | "DRAFT" |

### Response

| key            | 설명      | value 타입   | 옵션     | Nullable | 예시                                   |
| -------------- | --------- | ------------ | -------- | -------- | -------------------------------------- |
| videoId        | 영상 ID   | string(uuid) | required | false    | "550e8400-e29b-41d4-a716-446655440001" |
| previousStatus | 이전 상태 | string       | required | false    | "DRAFT"                                |
| currentStatus  | 현재 상태 | string       | required | false    | "READY"                                |
| updatedAt      | 변경 시각 | string       | required | false    | "2025-12-24T10:00:00Z"                 |

### Status

| status        | response content    |
| ------------- | ------------------- |
| 200 OK        | 정상                |
| 404 Not Found | 영상을 찾을 수 없음 |
| 401/403       | 인증/권한 오류      |

---

# 2. Video Job (영상 생성 작업)

## 2.1 영상 생성 요청

### ✔ URL

- POST /video/job

### ✔ 설명

- 최종 확정된 스크립트를 기반으로 영상 생성 Job을 등록합니다.
- AI 서버에 영상 생성 작업을 요청합니다.

### ✔ 권한

`ROLE_ADMIN`

### ✔ 요청

Body: 있음

### Request

| key      | 설명             | value 타입   | 옵션     | Nullable | 예시                                   |
| -------- | ---------------- | ------------ | -------- | -------- | -------------------------------------- |
| eduId    | 교육 ID          | string(uuid) | required | false    | "550e8400-e29b-41d4-a716-446655440000" |
| scriptId | 최종 스크립트 ID | string(uuid) | required | false    | "550e8400-e29b-41d4-a716-446655440001" |
| videoId  | 영상 컨텐츠 ID   | string(uuid) | required | false    | "550e8400-e29b-41d4-a716-446655440002" |

### Response

| key    | 설명          | value 타입   | 옵션     | Nullable | 예시                                   |
| ------ | ------------- | ------------ | -------- | -------- | -------------------------------------- |
| jobId  | 생성된 Job ID | string(uuid) | required | false    | "550e8400-e29b-41d4-a716-446655440003" |
| status | Job 상태      | string       | required | false    | "PENDING"                              |

### Status

| status                    | response content  |
| ------------------------- | ----------------- |
| 201 Created               | Job 생성 성공     |
| 400 Bad Request           | scriptId 누락     |
| 403 Forbidden             | 권한 없음         |
| 404 Not Found             | script/eduId 없음 |
| 500 Internal Server Error | Job 등록 실패     |

---

## 2.2 영상 생성 Job 목록 조회 (\* 개발용)

### ✔ URL

- GET /video/jobs

### ✔ 설명

- 영상 생성 Job 목록을 페이징으로 조회합니다.

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

| key        | 설명                | value 타입   | 옵션     | Nullable | 예시                                   |
| ---------- | ------------------- | ------------ | -------- | -------- | -------------------------------------- |
| jobId      | Job ID              | string(uuid) | required | false    | "550e8400-e29b-41d4-a716-446655440003" |
| scriptId   | 스크립트 ID         | string(uuid) | required | false    | "550e8400-e29b-41d4-a716-446655440001" |
| eduId      | 교육 ID             | string(uuid) | required | false    | "550e8400-e29b-41d4-a716-446655440000" |
| status     | 상태                | string       | required | false    | "PROCESSING"                           |
| retryCount | 재시도 횟수         | number       | required | false    | 0                                      |
| videoUrl   | 생성된 영상 URL     | string       | optional | true     | "https://cdn.com/video.mp4"            |
| duration   | 영상 길이(초)       | number       | optional | true     | 720                                    |
| createdAt  | 생성 시각 (ISO8601) | string       | required | false    | "2025-12-24T10:00:00Z"                 |
| updatedAt  | 수정 시각 (ISO8601) | string       | required | false    | "2025-12-24T10:05:00Z"                 |
| failReason | 실패 사유           | string       | optional | true     | "AI 서버 오류"                         |

### Status

| status  | response content |
| ------- | ---------------- |
| 200 OK  | 정상             |
| 401/403 | 인증/권한 오류   |

---

## 2.3 영상 생성 Job 상세 조회

### ✔ URL

- GET /video/job/{jobId}

### ✔ 설명

- 특정 Job의 상세 정보를 조회합니다.

### ✔ 권한

`ROLE_USER`

### ✔ 요청

Body: 없음

### Path

- jobId: UUID (Job ID)

### Response

| key        | 설명                | value 타입   | 옵션     | Nullable | 예시                                   |
| ---------- | ------------------- | ------------ | -------- | -------- | -------------------------------------- |
| jobId      | Job ID              | string(uuid) | required | false    | "550e8400-e29b-41d4-a716-446655440003" |
| scriptId   | 스크립트 ID         | string(uuid) | required | false    | "550e8400-e29b-41d4-a716-446655440001" |
| eduId      | 교육 ID             | string(uuid) | required | false    | "550e8400-e29b-41d4-a716-446655440000" |
| status     | 상태                | string       | required | false    | "PROCESSING"                           |
| retryCount | 재시도 횟수         | number       | required | false    | 0                                      |
| videoUrl   | 생성된 영상 URL     | string       | optional | true     | "https://cdn.com/video.mp4"            |
| duration   | 영상 길이(초)       | number       | optional | true     | 720                                    |
| createdAt  | 생성 시각 (ISO8601) | string       | required | false    | "2025-12-24T10:00:00Z"                 |
| updatedAt  | 수정 시각 (ISO8601) | string       | required | false    | "2025-12-24T10:05:00Z"                 |
| failReason | 실패 사유           | string       | optional | true     | "AI 서버 오류"                         |

### Status

| status        | response content   |
| ------------- | ------------------ |
| 200 OK        | 정상               |
| 404 Not Found | Job을 찾을 수 없음 |
| 401/403       | 인증/권한 오류     |

---

## 2.4 영상 생성 Job 수정 (\* 개발용)

### ✔ URL

- PUT /video/job/{jobId}

### ✔ 설명

- Job 상태/비고 등을 수정합니다.

### ✔ 권한

`ROLE_ADMIN`

### ✔ 요청

Body: 있음

### Path

- jobId: UUID (Job ID)

### Request

| key        | 설명                         | value 타입 | 옵션     | Nullable | 예시                                |
| ---------- | ---------------------------- | ---------- | -------- | -------- | ----------------------------------- |
| status     | 새 상태                      | string     | optional | true     | "CANCELLED"                         |
| failReason | 실패 사유 또는 메모          | string     | optional | true     | "수동 취소/오류 메시지 등"          |
| videoUrl   | 생성된 영상 URL(수정 필요시) | string     | optional | true     | "https://cdn.example.com/video.mp4" |
| duration   | 영상 길이(초)                | number     | optional | true     | 120                                 |

### Response

| key        | 설명                | value 타입   | 옵션     | Nullable | 예시                                   |
| ---------- | ------------------- | ------------ | -------- | -------- | -------------------------------------- |
| jobId      | Job ID              | string(uuid) | required | false    | "550e8400-e29b-41d4-a716-446655440003" |
| scriptId   | 스크립트 ID         | string(uuid) | required | false    | "550e8400-e29b-41d4-a716-446655440001" |
| eduId      | 교육 ID             | string(uuid) | required | false    | "550e8400-e29b-41d4-a716-446655440000" |
| status     | 상태                | string       | required | false    | "CANCELLED"                            |
| retryCount | 재시도 횟수         | number       | required | false    | 0                                      |
| videoUrl   | 생성된 영상 URL     | string       | optional | true     | "https://cdn.example.com/video.mp4"    |
| duration   | 영상 길이(초)       | number       | optional | true     | 120                                    |
| createdAt  | 생성 시각 (ISO8601) | string       | required | false    | "2025-12-24T10:00:00Z"                 |
| updatedAt  | 수정 시각 (ISO8601) | string       | required | false    | "2025-12-24T10:05:00Z"                 |
| failReason | 실패 사유           | string       | optional | true     | "수동 취소"                            |

### Status

| status        | response content   |
| ------------- | ------------------ |
| 200 OK        | 정상               |
| 404 Not Found | Job을 찾을 수 없음 |
| 401/403       | 인증/권한 오류     |

---

## 2.5 영상 생성 Job 삭제 (\* 개발용)

### ✔ URL

- DELETE /video/job/{jobId}

### ✔ 설명

- 특정 Job을 삭제합니다.

### ✔ 권한

`ROLE_ADMIN`

### ✔ 요청

Body: 없음

### Path

- jobId: UUID (Job ID)

### Response

없음 (204 No Content)

### Status

| status         | response content   |
| -------------- | ------------------ |
| 204 No Content | 삭제 성공          |
| 404 Not Found  | Job을 찾을 수 없음 |
| 401/403        | 인증/권한 오류     |

---

## 2.6 영상 생성 재시도

### ✔ URL

- POST /video/job/{jobId}/retry

### ✔ 설명

- 이전 영상 생성 Job이 FAILED 상태일 때 재시도합니다.

### ✔ 권한

`ROLE_ADMIN`

### ✔ 요청

Body: 없음

### Path

- jobId: UUID (Job ID)

### Response

| key        | 설명             | value 타입   | 옵션     | Nullable | 예시                                   |
| ---------- | ---------------- | ------------ | -------- | -------- | -------------------------------------- |
| jobId      | Job ID           | string(uuid) | required | false    | "550e8400-e29b-41d4-a716-446655440003" |
| status     | 재시도 후 상태   | string       | required | false    | "PENDING"                              |
| retryCount | 누적 재시도 횟수 | number       | required | false    | 2                                      |

### Status

| status                    | response content                           |
| ------------------------- | ------------------------------------------ |
| 200 OK                    | 정상                                       |
| 400 Bad Request           | job 상태가 FAILED가 아님                   |
| 401 Unauthorized          | 인증 실패                                  |
| 403 Forbidden             | ROLE_ADMIN 아님                            |
| 404 Not Found             | jobId 없음                                 |
| 409 Conflict              | 이미 실행 중(PROCESSING)인 job 재시도 불가 |
| 500 Internal Server Error | AI 서버 재요청 실패                        |

---

# 3. Internal Video API (AI 서버 ↔ 백엔드)

## 3.1 영상 생성 완료 콜백 (내부 API)

### ✔ URL

- POST /video/job/{jobId}/complete

### ✔ 설명

- AI 서버가 영상 생성 완료 후 백엔드로 결과를 전달합니다.
- 영상 파일 URL을 저장하고 영상 상태를 `READY`로 변경합니다.

### ✔ 권한

내부 토큰 (`X-Internal-Token`)

### ✔ 요청

Body: 있음

### Path

- jobId: UUID (Job ID)

### Request

| key      | 설명            | value 타입 | 옵션     | Nullable | 예시                        |
| -------- | --------------- | ---------- | -------- | -------- | --------------------------- |
| videoUrl | 생성된 영상 URL | string     | optional | true     | "https://cdn.com/video.mp4" |
| duration | 영상 길이(초)   | number     | optional | true     | 720                         |
| status   | 완료 상태       | string     | required | false    | "COMPLETED"                 |

### Response

| key   | 설명           | value 타입 | 옵션     | Nullable | 예시 |
| ----- | -------------- | ---------- | -------- | -------- | ---- |
| saved | 저장 성공 여부 | boolean    | required | false    | true |

### Status

| status          | response content   |
| --------------- | ------------------ |
| 200 OK          | 콜백 처리 성공     |
| 400 Bad Request | 잘못된 요청        |
| 404 Not Found   | Job을 찾을 수 없음 |

---

# 4. Video Review (Admin)

## 4.1 검토 목록 조회

### ✔ URL

- GET /admin/videos/review-queue

### ✔ 설명

- 검토 대기/승인됨/반려됨 영상 목록을 조회합니다.
- 필터링, 정렬, 페이징을 지원합니다.

### ✔ 권한

`ROLE_ADMIN`

### ✔ 요청

Body: 없음

### Query Parameter

| key              | 설명                                                      | value 타입 | 옵션     | Nullable | 예시     |
| ---------------- | --------------------------------------------------------- | ---------- | -------- | -------- | -------- |
| page             | 페이지 번호 (0-base)                                      | number     | optional | false    | 0        |
| size             | 페이지 크기                                               | number     | optional | false    | 30       |
| search           | 검색어 (제목/교육 제목/부서/제작자)                      | string     | optional | true     | "성희롱" |
| myProcessingOnly | 내 처리만 (현재 사용자가 검토한 영상만)                   | boolean    | optional | true     | false    |
| status           | 상태 필터 (pending: 검토 대기, approved: 승인됨, rejected: 반려됨) | string     | optional | true     | "pending" |
| reviewStage      | 검토 단계 필터 (first: 1차, second: 2차, document: 문서, all: 전체) | string     | optional | true     | "first"   |
| sort             | 정렬 옵션 (latest: 최신순, oldest: 오래된순, title: 제목순) | string     | optional | true     | "latest" |

### Response

| key              | 설명                | value 타입    | 옵션     | Nullable | 예시 |
| ---------------- | ------------------- | ------------- | -------- | -------- | ---- |
| items            | 검토 목록           | array(object) | required | false    | 아래 표 참조 |
| totalCount       | 전체 개수           | number        | required | false    | 100  |
| page             | 현재 페이지 (0-base) | number        | required | false    | 0    |
| size             | 페이지 크기         | number        | required | false    | 30   |
| totalPages       | 전체 페이지 수      | number        | required | false    | 4    |
| firstRoundCount  | 1차 검토 대기 개수  | number        | required | false    | 8    |
| secondRoundCount | 2차 검토 대기 개수  | number        | required | false    | 19   |
| documentCount    | 문서 타입 개수      | number        | required | false    | 36   |

items item

| key              | 설명           | value 타입   | 옵션     | Nullable | 예시                                   |
| ---------------- | -------------- | ------------ | -------- | -------- | -------------------------------------- |
| videoId          | 영상 ID        | string(uuid) | required | false    | "550e8400-e29b-41d4-a716-446655440001" |
| educationId      | 교육 ID        | string(uuid) | required | false    | "550e8400-e29b-41d4-a716-446655440000" |
| educationTitle   | 교육 제목      | string       | required | false    | "성희롱 예방 교육"                     |
| videoTitle       | 영상 제목      | string       | required | false    | "2024년 성희롱 예방 교육"              |
| status           | 상태           | string       | required | false    | "SCRIPT_REVIEW_REQUESTED"              |
| reviewStage      | 검토 단계      | string       | required | false    | "1차 검토"                             |
| creatorDepartment | 제작자 부서    | string       | optional | true     | "총무팀"                               |
| creatorName      | 제작자 이름    | string       | optional | true     | "홍길동"                               |
| creatorUuid      | 제작자 UUID    | string(uuid) | optional | true     | "550e8400-e29b-41d4-a716-446655440002" |
| submittedAt      | 제출 시각      | string       | required | false    | "2025-12-24T10:00:00Z"                 |
| category         | 카테고리       | string       | optional | true     | "SEXUAL_HARASSMENT_PREVENTION"         |
| eduType          | 교육 유형      | string       | optional | true     | "MANDATORY"                            |

### Status

| status  | response content |
| ------- | ---------------- |
| 200 OK  | 정상             |
| 401/403 | 인증/권한 오류   |

---

## 4.2 검토 통계 조회

### ✔ URL

- GET /admin/videos/review-stats

### ✔ 설명

- 검토 대기, 승인됨, 반려됨, 내 활동 개수를 조회합니다.

### ✔ 권한

`ROLE_ADMIN`

### ✔ 요청

Body: 없음

### Query Parameter

없음

### Response

| key            | 설명          | value 타입 | 옵션     | Nullable | 예시 |
| -------------- | ------------- | ---------- | -------- | -------- | ---- |
| pendingCount   | 검토 대기 개수 | number     | required | false    | 27   |
| approvedCount  | 승인됨 개수   | number     | required | false    | 150  |
| rejectedCount  | 반려됨 개수   | number     | required | false    | 5    |
| myActivityCount | 내 활동 개수 | number     | required | false    | 12   |

### Status

| status  | response content |
| ------- | ---------------- |
| 200 OK  | 정상             |
| 401/403 | 인증/권한 오류   |

---

## 4.3 영상 감사 이력 조회

### ✔ URL

- GET /admin/videos/{videoId}/review-history

### ✔ 설명

- 특정 영상의 감사 이력(생성, 검토 요청, 승인, 반려 등)을 조회합니다.

### ✔ 권한

`ROLE_ADMIN`

### ✔ 요청

Body: 없음

### Path

- videoId: UUID (영상 ID)

### Response

| key         | 설명          | value 타입    | 옵션     | Nullable | 예시                                   |
| ----------- | ------------- | ------------- | -------- | -------- | -------------------------------------- |
| videoId     | 영상 ID       | string(uuid)  | required | false    | "550e8400-e29b-41d4-a716-446655440001" |
| videoTitle  | 영상 제목     | string        | required | false    | "2024년 성희롱 예방 교육"              |
| history     | 감사 이력 목록 | array(object) | required | false    | 아래 표 참조                           |

history item

| key             | 설명                | value 타입   | 옵션     | Nullable | 예시                      |
| --------------- | ------------------- | ------------ | -------- | -------- | ------------------------- |
| eventType       | 이벤트 타입         | string       | required | false    | "CREATED", "REJECTED"     |
| description     | 이벤트 설명         | string       | required | false    | "영상 생성", "검토 반려"  |
| timestamp       | 발생 시각           | string       | required | false    | "2025-12-24T10:00:00Z"    |
| actorName       | 처리자 이름         | string       | required | false    | "홍길동", "SYSTEM"         |
| actorUuid       | 처리자 UUID         | string(uuid) | optional | true     | "550e8400-e29b-41d4-a716-446655440002" |
| rejectionReason | 반려 사유 (반려인 경우) | string       | optional | true     | "스크립트 내용 수정 필요" |
| rejectionStage  | 반려 단계 (반려인 경우) | string       | optional | true     | "SCRIPT", "VIDEO"         |

### Status

| status        | response content    |
| ------------- | ------------------- |
| 200 OK        | 정상                |
| 404 Not Found | 영상을 찾을 수 없음 |
| 401/403       | 인증/권한 오류      |

---

## 4.4 검토 상세 정보 조회

### ✔ URL

- GET /admin/videos/{videoId}/review-detail

### ✔ 설명

- 검토 화면에서 필요한 영상의 상세 정보를 조회합니다.
- 영상 정보, 제작자 정보, 스크립트 정보 등을 포함합니다.

### ✔ 권한

`ROLE_ADMIN`

### ✔ 요청

Body: 없음

### Path

- videoId: UUID (영상 ID)

### Response

| key              | 설명           | value 타입   | 옵션     | Nullable | 예시                                   |
| ---------------- | -------------- | ------------ | -------- | -------- | -------------------------------------- |
| videoId          | 영상 ID        | string(uuid) | required | false    | "550e8400-e29b-41d4-a716-446655440001" |
| educationId      | 교육 ID        | string(uuid) | required | false    | "550e8400-e29b-41d4-a716-446655440000" |
| educationTitle   | 교육 제목      | string       | required | false    | "성희롱 예방 교육"                     |
| videoTitle       | 영상 제목      | string       | required | false    | "2024년 성희롱 예방 교육"              |
| status           | 상태           | string       | required | false    | "SCRIPT_REVIEW_REQUESTED"              |
| reviewStage      | 검토 단계      | string       | required | false    | "1차 검토"                             |
| creatorDepartment | 제작자 부서    | string       | optional | true     | "총무팀"                               |
| creatorName      | 제작자 이름    | string       | optional | true     | "홍길동"                               |
| creatorUuid      | 제작자 UUID    | string(uuid) | optional | true     | "550e8400-e29b-41d4-a716-446655440002" |
| submittedAt      | 제출 시각      | string       | required | false    | "2025-12-24T10:00:00Z"                 |
| updatedAt        | 업데이트 시각  | string       | required | false    | "2025-12-24T11:00:00Z"                 |
| category         | 카테고리       | string       | optional | true     | "SEXUAL_HARASSMENT_PREVENTION"          |
| eduType          | 교육 유형      | string       | optional | true     | "MANDATORY"                             |
| scriptId         | 스크립트 ID    | string(uuid) | optional | true     | "550e8400-e29b-41d4-a716-446655440003" |
| scriptVersion    | 스크립트 버전  | number       | optional | true     | 1                                      |

### Status

| status        | response content    |
| ------------- | ------------------- |
| 200 OK        | 정상                |
| 404 Not Found | 영상을 찾을 수 없음 |
| 401/403       | 인증/권한 오류      |

---

## 주의사항

1. **상태 전이**: 영상 상태는 다음 순서로 전이됩니다:
   - `DRAFT` → `SCRIPT_READY` → `SCRIPT_REVIEW_REQUESTED` → `SCRIPT_APPROVED` → `PROCESSING` → `READY` → `FINAL_REVIEW_REQUESTED` → `PUBLISHED` → `DISABLED` (비활성화 가능)
   - 반려 시: `SCRIPT_REVIEW_REQUESTED` → `SCRIPT_READY` (1차 반려), `FINAL_REVIEW_REQUESTED` → `READY` (2차 반려)
   - 비활성화/활성화: `PUBLISHED` ↔ `DISABLED`
2. **영상 생성**: 영상 생성 Job은 비동기로 처리되며, 완료까지 시간이 걸릴 수 있습니다.
3. **Job 상태**: Job 상태는 `PENDING` → `PROCESSING` → `COMPLETED` 또는 `FAILED`로 변경됩니다.
4. **재시도**: FAILED 상태의 Job만 재시도할 수 있습니다.
5. **내부 API**: `/video/job/{jobId}/complete` 엔드포인트는 내부 토큰(`X-Internal-Token`)이 필요하며, 외부에서 직접 호출하지 않습니다.

---

## 관련 문서

- [SourceSet API 명세](../source-set/api/source_set_api_spec.md)
- [Script API 명세](../script/api/script_api_spec.md)
- [Video Creation Workflow](../flow/video_creation_workflow.md)
