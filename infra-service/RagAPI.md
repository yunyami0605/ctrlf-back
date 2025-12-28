## RAG Documents API

Base URL: http://localhost:9003

---

# 1. RAG Documents

## 1.1 문서 업로드 메타 등록

### ✔ URL

- POST /rag/documents/upload

### ✔ 설명

- S3에 파일 업로드 완료 후 문서 메타(title, domain, uploaderUuid, fileUrl)를 저장합니다.

### ✔ 권한

`ROLE_ADMIN` (운영 정책에 따라 조정)

### ✔ 요청

Body: 있음

### Request

| key          | 설명                  | value 타입 | 옵션     | Nullable | 예시                                   |
| ------------ | --------------------- | ---------- | -------- | -------- | -------------------------------------- |
| title        | 문서 제목             | string     | required | false    | "산업안전 규정집 v3"                   |
| domain       | 문서 도메인           | string     | required | false    | "HR"                                   |
| uploaderUuid | 업로더 UUID           | string     | required | false    | "c13c91f2-fb1a-4d42-b381-72847a52fb99" |
| fileUrl      | 업로드된 S3 파일 경로 | string     | required | false    | "s3://ctrl-s3/docs/hr_safety_v3.pdf"   |

### Response

| key        | 설명               | value 타입 | 예시                   |
| ---------- | ------------------ | ---------- | ---------------------- |
| documentId | 생성된 문서 ID     | string     | "f3b2d1..."            |
| status     | 초기 상태          | string     | "QUEUED"               |
| createdAt  | 생성 시각(ISO8601) | string     | "2025-12-17T10:00:00Z" |

### Status

| status          | response content |
| --------------- | ---------------- |
| 201 Created     | 정상 생성        |
| 400/401/403/500 | 오류             |

---

## 1.2 문서 목록 조회

### ✔ URL

- GET /rag/documents

### ✔ 설명

- 등록된 문서 목록을 조회합니다. domain/uploaderUuid/date range/keyword 필터와 page/size 페이징 지원.

### ✔ 권한

`ROLE_USER`

### ✔ 요청

Body: 없음

### Query Parameter

| key          | 설명                  | value 타입 | 옵션     | Nullable | 예시           |
| ------------ | --------------------- | ---------- | -------- | -------- | -------------- |
| domain       | 도메인                | string     | optional | true     | "HR"           |
| uploaderUuid | 업로더 UUID           | string     | optional | true     | "c13c91f2-..." |
| startDate    | 기간 시작(yyyy-MM-dd) | string     | optional | true     | "2025-01-01"   |
| endDate      | 기간 끝(yyyy-MM-dd)   | string     | optional | true     | "2025-12-31"   |
| keyword      | 제목 키워드           | string     | optional | true     | "산업안전"     |
| page         | 페이지 번호(0-base)   | number     | optional | true     | 0              |
| size         | 페이지 크기           | number     | optional | true     | 10             |

### Response

array of object

| key          | 설명      | value 타입 |
| ------------ | --------- | ---------- |
| id           | 문서 ID   | string     |
| title        | 제목      | string     |
| domain       | 도메인    | string     |
| uploaderUuid | 업로더    | string     |
| createdAt    | 생성 시각 | string     |

### Status

| status          | response content |
| --------------- | ---------------- |
| 200 OK          | 정상             |
| 400/401/403/500 | 오류             |

---

## 1.3 문서 수정(Update)

### ✔ URL

- PATCH /rag/documents/{id}

### ✔ 설명

- 문서 메타 일부를 수정합니다. (제목/도메인/fileUrl)

### ✔ 권한

`ROLE_ADMIN`

### ✔ 요청

Body: 있음

### Request

| key     | 설명      | 타입   | Nullable |
| ------- | --------- | ------ | -------- |
| title   | 문서 제목 | string | true     |
| domain  | 도메인    | string | true     |
| fileUrl | S3 경로   | string | true     |

### Response

| key        | 설명               | 타입   |
| ---------- | ------------------ | ------ |
| documentId | 문서 ID            | string |
| status     | 상태               | string |
| updatedAt  | 수정 시각(ISO8601) | string |

### Status

200

---

## 1.4 문서 삭제(Delete)

### ✔ URL

- DELETE /rag/documents/{id}

### ✔ 설명

- 문서를 삭제합니다.

### ✔ 권한

`ROLE_ADMIN`

### Response

| key        | 설명      | 타입   |
| ---------- | --------- | ------ |
| documentId | 문서 ID   | string |
| status     | "DELETED" | string |
| deletedAt  | 삭제 시각 | string |

### Status

200

---

## 1.5 문서 재처리 요청

### ✔ URL

- POST /rag/documents/{id}/reprocess

### ✔ 설명

- 문서 임베딩/처리를 재요청합니다.

### ✔ 권한

`ROLE_ADMIN`

### ✔ 요청

Body: 있음(선택 필드)

| key         | 설명        | 타입   | Nullable |
| ----------- | ----------- | ------ | -------- |
| title       | 문서 제목   | string | true     |
| domain      | 도메인      | string | true     |
| fileUrl     | S3 경로     | string | true     |
| requestedBy | 요청자 UUID | string | true     |

### Response

| key        | 설명      | 타입    |
| ---------- | --------- | ------- |
| documentId | 문서 ID   | string  |
| accepted   | 접수 여부 | boolean |
| status     | 처리 상태 | string  |
| jobId      | 작업 ID   | string  |
| updatedAt  | 접수 시각 | string  |

### Status

202
