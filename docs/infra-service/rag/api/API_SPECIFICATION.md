# RAG Documents API 명세서

## 개요

이 문서는 `RagDocumentsController`에 구현된 모든 RAG 문서 관리 API의 상세 명세를 제공합니다.

## 기본 정보

- **Base URL**: `http://localhost:9003`
- **인증 방식**: JWT Bearer Token (일부 내부 API 제외)
- **응답 형식**: JSON
- **문자 인코딩**: UTF-8

## 공통 헤더

### 인증이 필요한 API

```
Authorization: Bearer {JWT_TOKEN}
Content-Type: application/json
```

### 내부 API

```
X-Internal-Token: {INTERNAL_TOKEN}
Content-Type: application/json
```

---

## 1. RAG 문서 관리 API

### 1.1 문서 업로드 메타 등록

문서를 S3에 업로드한 후 메타 정보를 등록합니다.

**엔드포인트**

```
POST /rag/documents/upload
```

**인증**: ✅ JWT Bearer Token 필요

**요청 Body**

```json
{
  "title": "산업안전 규정집 v3",
  "domain": "HR",
  "fileUrl": "s3://ctrl-s3/docs/hr_safety_v3.pdf"
}
```

| 필드      | 타입   | 필수 | 설명                           |
| --------- | ------ | ---- | ------------------------------ |
| `title`   | string | ✅   | 문서 제목                      |
| `domain`  | string | ✅   | 문서 도메인 (예: HR, SEC, EDU) |
| `fileUrl` | string | ✅   | S3 파일 URL                    |

**응답 (201 Created)**

```json
{
  "documentId": "024776fa-009b-4161-ab3b-33c974a3844a",
  "status": "QUEUED",
  "createdAt": "2025-12-29T12:34:56Z"
}
```

**에러 응답**

- `400 Bad Request`: 잘못된 요청
- `401 Unauthorized`: 인증 실패
- `403 Forbidden`: 권한 없음
- `500 Internal Server Error`: 서버 오류

---

### 1.2 문서 목록 조회

등록된 문서 목록을 필터링 및 페이징하여 조회합니다.

**엔드포인트**

```
GET /rag/documents
```

**인증**: ✅ JWT Bearer Token 필요

**Query Parameters**

| 파라미터       | 타입    | 필수 | 설명                     |
| -------------- | ------- | ---- | ------------------------ |
| `domain`       | string  | ❌   | 문서 도메인 필터         |
| `uploaderUuid` | string  | ❌   | 업로더 UUID 필터         |
| `startDate`    | string  | ❌   | 기간 시작 (yyyy-MM-dd)   |
| `endDate`      | string  | ❌   | 기간 끝 (yyyy-MM-dd)     |
| `keyword`      | string  | ❌   | 제목 키워드 검색         |
| `page`         | integer | ❌   | 페이지 번호 (기본값: 0)  |
| `size`         | integer | ❌   | 페이지 크기 (기본값: 10) |

**응답 (200 OK)**

```json
[
  {
    "id": "024776fa-009b-4161-ab3b-33c974a3844a",
    "title": "산업안전 규정집 v3",
    "domain": "HR",
    "uploaderUuid": "c13c91f2-fb1a-4d42-b381-72847a52fb99",
    "createdAt": "2025-12-29T12:34:56Z"
  }
]
```

**에러 응답**

- `400 Bad Request`: 잘못된 필터 값
- `401 Unauthorized`: 인증 실패
- `403 Forbidden`: 권한 없음
- `500 Internal Server Error`: 서버 오류

---

### 1.3 문서 수정

문서의 메타 정보를 수정합니다.

**엔드포인트**

```
PATCH /rag/documents/{id}
```

**인증**: ❌ (명시되지 않음)

**Path Parameters**

| 파라미터 | 타입   | 필수 | 설명           |
| -------- | ------ | ---- | -------------- |
| `id`     | string | ✅   | 문서 ID (UUID) |

**요청 Body**

```json
{
  "title": "산업안전 규정집 v4",
  "domain": "HR",
  "fileUrl": "s3://ctrl-s3/docs/file_v4.pdf"
}
```

| 필드      | 타입   | 필수 | 설명        |
| --------- | ------ | ---- | ----------- |
| `title`   | string | ❌   | 문서 제목   |
| `domain`  | string | ❌   | 문서 도메인 |
| `fileUrl` | string | ❌   | S3 파일 URL |

**응답 (200 OK)**

```json
{
  "documentId": "024776fa-009b-4161-ab3b-33c974a3844a",
  "status": "REPROCESSING",
  "updatedAt": "2025-12-29T12:34:56Z"
}
```

---

### 1.4 문서 삭제

문서를 삭제합니다.

**엔드포인트**

```
DELETE /rag/documents/{id}
```

**인증**: ❌ (명시되지 않음)

**Path Parameters**

| 파라미터 | 타입   | 필수 | 설명           |
| -------- | ------ | ---- | -------------- |
| `id`     | string | ✅   | 문서 ID (UUID) |

**응답 (200 OK)**

```json
{
  "documentId": "024776fa-009b-4161-ab3b-33c974a3844a",
  "status": "DELETED",
  "deletedAt": "2025-12-29T12:34:56Z"
}
```

---

### 1.5 문서 재처리 요청

문서의 재처리를 요청합니다.

**엔드포인트**

```
POST /rag/documents/{id}/reprocess
```

**인증**: ❌ (명시되지 않음)

**Path Parameters**

| 파라미터 | 타입   | 필수 | 설명           |
| -------- | ------ | ---- | -------------- |
| `id`     | string | ✅   | 문서 ID (UUID) |

**요청 Body**

```json
{
  "title": "산업안전 규정집 v4",
  "domain": "HR",
  "fileUrl": "s3://ctrl-s3/docs/new.pdf",
  "requestedBy": "c13c91f2-fb1a-4d42-b381-72847a52fb99"
}
```

| 필드          | 타입   | 필수 | 설명        |
| ------------- | ------ | ---- | ----------- |
| `title`       | string | ❌   | 문서 제목   |
| `domain`      | string | ❌   | 문서 도메인 |
| `fileUrl`     | string | ❌   | S3 파일 URL |
| `requestedBy` | string | ❌   | 요청자 UUID |

**응답 (202 Accepted)**

```json
{
  "documentId": "024776fa-009b-4161-ab3b-33c974a3844a",
  "accepted": true,
  "status": "REPROCESSING",
  "jobId": "job-12345",
  "updatedAt": "2025-12-29T12:34:56Z"
}
```

---

### 1.6 문서 처리 상태 조회

문서의 임베딩 처리 상태를 조회합니다.

**엔드포인트**

```
GET /rag/documents/{id}/status
```

**인증**: ❌ (명시되지 않음)

**Path Parameters**

| 파라미터 | 타입   | 필수 | 설명           |
| -------- | ------ | ---- | -------------- |
| `id`     | string | ✅   | 문서 ID (UUID) |

**응답 (200 OK)**

```json
{
  "documentId": "024776fa-009b-4161-ab3b-33c974a3844a",
  "status": "COMPLETED",
  "createdAt": "2025-12-29T12:34:56Z",
  "processedAt": "2025-12-29T12:35:00Z"
}
```

**상태 값**

- `QUEUED`: 대기 중
- `PROCESSING`: 처리 중
- `COMPLETED`: 처리 완료
- `FAILED`: 처리 실패

**에러 응답**

- `404 Not Found`: 문서를 찾을 수 없음

---

### 1.7 문서 정보 조회

문서의 메타 정보를 조회합니다.

**엔드포인트**

```
GET /rag/documents/{id}
```

**인증**: ❌ (명시되지 않음)

**Path Parameters**

| 파라미터 | 타입   | 필수 | 설명           |
| -------- | ------ | ---- | -------------- |
| `id`     | string | ✅   | 문서 ID (UUID) |

**응답 (200 OK)**

```json
{
  "id": "024776fa-009b-4161-ab3b-33c974a3844a",
  "title": "산업안전 규정집 v3",
  "domain": "HR",
  "sourceUrl": "s3://ctrl-s3/docs/hr_safety_v3.pdf",
  "status": "COMPLETED"
}
```

**에러 응답**

- `404 Not Found`: 문서를 찾을 수 없음

---

### 1.8 문서 원문 텍스트 조회

문서의 원문 텍스트를 조회합니다. S3에서 파일을 다운로드하여 텍스트를 추출합니다.

**엔드포인트**

```
GET /rag/documents/{id}/text
```

**인증**: ❌ (명시되지 않음)

**Path Parameters**

| 파라미터 | 타입   | 필수 | 설명           |
| -------- | ------ | ---- | -------------- |
| `id`     | string | ✅   | 문서 ID (UUID) |

**응답 (200 OK)**

```json
{
  "documentId": "024776fa-009b-4161-ab3b-33c974a3844a",
  "text": "추출된 원문 텍스트 내용..."
}
```

**에러 응답**

- `404 Not Found`: 문서를 찾을 수 없음
- `500 Internal Server Error`: 텍스트 추출 실패

---

## 2. 내부 API (FastAPI → Spring)

### 2.1 문서 청크 Bulk Upsert

FastAPI가 문서 청크를 bulk upsert합니다. 임베딩 벡터는 Milvus에 저장되고, DB에는 chunk_text만 저장됩니다.

**엔드포인트**

```
POST /rag/documents/{documentId}/chunks:bulk
```

**인증**: 내부 토큰 (X-Internal-Token)

**Path Parameters**

| 파라미터     | 타입   | 필수 | 설명           |
| ------------ | ------ | ---- | -------------- |
| `documentId` | string | ✅   | 문서 ID (UUID) |

**요청 Body**

```json
{
  "chunks": [
    {
      "chunkIndex": 0,
      "chunkText": "첫 번째 청크 텍스트",
      "chunkMeta": {}
    },
    {
      "chunkIndex": 1,
      "chunkText": "두 번째 청크 텍스트",
      "chunkMeta": {}
    }
  ],
  "requestId": "request-12345"
}
```

| 필드                  | 타입    | 필수 | 설명        |
| --------------------- | ------- | ---- | ----------- |
| `chunks`              | array   | ✅   | 청크 리스트 |
| `chunks[].chunkIndex` | integer | ✅   | 청크 번호   |
| `chunks[].chunkText`  | string  | ✅   | 청크 텍스트 |
| `chunks[].chunkMeta`  | object  | ❌   | 메타데이터  |
| `requestId`           | string  | ❌   | 멱등 키     |

**응답 (200 OK)**

```json
{
  "saved": true,
  "savedCount": 2
}
```

**에러 응답**

- `400 Bad Request`: 잘못된 요청
- `401 Unauthorized`: 내부 토큰 오류
- `404 Not Found`: 문서를 찾을 수 없음

---

### 2.2 임베딩 실패 로그 Bulk Upsert

FastAPI가 임베딩 실패한 청크 로그를 bulk upsert합니다.

**엔드포인트**

```
POST /rag/documents/{documentId}/fail-chunks:bulk
```

**인증**: 내부 토큰 (X-Internal-Token)

**Path Parameters**

| 파라미터     | 타입   | 필수 | 설명           |
| ------------ | ------ | ---- | -------------- |
| `documentId` | string | ✅   | 문서 ID (UUID) |

**요청 Body**

```json
{
  "fails": [
    {
      "chunkIndex": 0,
      "failReason": "임베딩 처리 실패: 텍스트가 너무 짧습니다"
    }
  ],
  "requestId": "request-12345"
}
```

| 필드                 | 타입    | 필수 | 설명             |
| -------------------- | ------- | ---- | ---------------- |
| `fails`              | array   | ✅   | 실패 청크 리스트 |
| `fails[].chunkIndex` | integer | ✅   | 청크 번호        |
| `fails[].failReason` | string  | ✅   | 실패 사유        |
| `requestId`          | string  | ❌   | 멱등 키          |

**응답 (200 OK)**

```json
{
  "saved": true,
  "savedCount": 1
}
```

**에러 응답**

- `400 Bad Request`: 잘못된 요청
- `401 Unauthorized`: 내부 토큰 오류
- `404 Not Found`: 문서를 찾을 수 없음

---

## 3. 사규 관리 API

### 3.1 사규 목록 조회

사규 목록을 document_id별로 그룹화하여 조회합니다. 검색, 상태 필터, 페이지네이션을 지원합니다.

**엔드포인트**

```
GET /rag/documents/policies
```

**인증**: ❌ (명시되지 않음)

**Query Parameters**

| 파라미터 | 타입    | 필수 | 설명                                                                                                        |
| -------- | ------- | ---- | ----------------------------------------------------------------------------------------------------------- |
| `search` | string  | ❌   | document_id 또는 제목 검색어                                                                                |
| `status` | string  | ❌   | 상태 필터 (ACTIVE, DRAFT, PENDING, ARCHIVED, 전체). ARCHIVED를 조회하려면 status=ARCHIVED로 명시적으로 지정 |
| `page`   | integer | ❌   | 페이지 번호 (기본값: 0)                                                                                     |
| `size`   | integer | ❌   | 페이지 크기 (기본값: 20)                                                                                    |

**응답 (200 OK)**

```json
{
  "items": [
    {
      "id": "024776fa-009b-4161-ab3b-33c974a3844a",
      "documentId": "POL-EDU-015",
      "title": "교육/퀴즈 운영 정책",
      "domain": "EDU",
      "versions": [
        {
          "version": 2,
          "status": "ACTIVE",
          "createdAt": "2025-12-29T12:34:56Z"
        },
        {
          "version": 1,
          "status": "DRAFT",
          "createdAt": "2025-12-28T10:00:00Z"
        }
      ],
      "totalVersions": 2
    }
  ],
  "page": 0,
  "size": 20,
  "total": 1
}
```

**에러 응답**

- `400 Bad Request`: 잘못된 필터 값

---

### 3.2 사규 상세 조회

document_id로 사규의 모든 버전을 조회합니다.

**엔드포인트**

```
GET /rag/documents/policies/{documentId}
```

**인증**: ❌ (명시되지 않음)

**Path Parameters**

| 파라미터     | 타입   | 필수 | 설명                               |
| ------------ | ------ | ---- | ---------------------------------- |
| `documentId` | string | ✅   | 사규 document_id (예: POL-EDU-015) |

**응답 (200 OK)**

```json
{
  "documentId": "POL-EDU-015",
  "title": "교육/퀴즈 운영 정책",
  "domain": "EDU",
  "versions": [
    {
      "id": "024776fa-009b-4161-ab3b-33c974a3844a",
      "documentId": "POL-EDU-015",
      "title": "교육/퀴즈 운영 정책",
      "domain": "EDU",
      "version": 2,
      "status": "ACTIVE",
      "changeSummary": "퀴즈 리포트 및 배포 캘린더 추가",
      "sourceUrl": "s3://ctrl-s3/docs/policy_v2.pdf",
      "uploaderUuid": "c13c91f2-fb1a-4d42-b381-72847a52fb99",
      "createdAt": "2025-12-29T12:34:56Z",
      "processedAt": "2025-12-29T12:35:00Z"
    }
  ]
}
```

**에러 응답**

- `404 Not Found`: 사규를 찾을 수 없음

---

### 3.3 버전별 상세 조회

특정 버전의 상세 정보를 조회합니다.

**엔드포인트**

```
GET /rag/documents/policies/{documentId}/versions/{version}
```

**인증**: ❌ (명시되지 않음)

**Path Parameters**

| 파라미터     | 타입    | 필수 | 설명                               |
| ------------ | ------- | ---- | ---------------------------------- |
| `documentId` | string  | ✅   | 사규 document_id (예: POL-EDU-015) |
| `version`    | integer | ✅   | 버전 번호 (예: 1)                  |

**응답 (200 OK)**

```json
{
  "id": "024776fa-009b-4161-ab3b-33c974a3844a",
  "documentId": "POL-EDU-015",
  "title": "교육/퀴즈 운영 정책",
  "domain": "EDU",
  "version": 2,
  "status": "ACTIVE",
  "changeSummary": "퀴즈 리포트 및 배포 캘린더 추가",
  "sourceUrl": "s3://ctrl-s3/docs/policy_v2.pdf",
  "uploaderUuid": "c13c91f2-fb1a-4d42-b381-72847a52fb99",
  "createdAt": "2025-12-29T12:34:56Z",
  "processedAt": "2025-12-29T12:35:00Z"
}
```

**에러 응답**

- `404 Not Found`: 버전을 찾을 수 없음

---

### 3.4 버전 목록 조회

사규의 모든 버전 목록을 조회합니다.

**엔드포인트**

```
GET /rag/documents/policies/{documentId}/versions
```

**인증**: ❌ (명시되지 않음)

**Path Parameters**

| 파라미터     | 타입   | 필수 | 설명                               |
| ------------ | ------ | ---- | ---------------------------------- |
| `documentId` | string | ✅   | 사규 document_id (예: POL-EDU-015) |

**응답 (200 OK)**

```json
[
  {
    "id": "024776fa-009b-4161-ab3b-33c974a3844a",
    "documentId": "POL-EDU-015",
    "title": "교육/퀴즈 운영 정책",
    "domain": "EDU",
    "version": 2,
    "status": "ACTIVE",
    "changeSummary": "퀴즈 리포트 및 배포 캘린더 추가",
    "sourceUrl": "s3://ctrl-s3/docs/policy_v2.pdf",
    "uploaderUuid": "c13c91f2-fb1a-4d42-b381-72847a52fb99",
    "createdAt": "2025-12-29T12:34:56Z",
    "processedAt": "2025-12-29T12:35:00Z"
  }
]
```

**에러 응답**

- `404 Not Found`: 사규를 찾을 수 없음

---

### 3.5 새 사규 생성

새로운 사규를 생성합니다. 초기 버전(v1)이 DRAFT 상태로 생성됩니다.

**엔드포인트**

```
POST /rag/documents/policies
```

**인증**: ✅ JWT Bearer Token 필요

**요청 Body**

```json
{
  "documentId": "POL-EDU-015",
  "title": "교육/퀴즈 운영 정책",
  "domain": "EDU",
  "fileUrl": "s3://ctrl-s3/docs/hr_safety_v3.pdf",
  "changeSummary": "초기 사규 등록"
}
```

| 필드            | 타입   | 필수 | 설명                           |
| --------------- | ------ | ---- | ------------------------------ |
| `documentId`    | string | ✅   | 사규 문서 ID (예: POL-EDU-015) |
| `title`         | string | ✅   | 사규 제목                      |
| `domain`        | string | ✅   | 사규 도메인 (예: EDU, HR, SEC) |
| `fileUrl`       | string | ❌   | S3 파일 URL                    |
| `changeSummary` | string | ❌   | 변경 요약                      |

**응답 (201 Created)**

```json
{
  "id": "024776fa-009b-4161-ab3b-33c974a3844a",
  "documentId": "POL-EDU-015",
  "title": "교육/퀴즈 운영 정책",
  "version": 1,
  "status": "DRAFT",
  "createdAt": "2025-12-29T12:34:56Z"
}
```

**에러 응답**

- `400 Bad Request`: 잘못된 요청
- `409 Conflict`: 이미 존재하는 document_id

---

### 3.6 새 버전 생성

기존 사규의 새 버전을 생성합니다. 버전 번호는 자동으로 증가하며 DRAFT 상태로 생성됩니다.

**엔드포인트**

```
POST /rag/documents/policies/{documentId}/versions
```

**인증**: ✅ JWT Bearer Token 필요

**Path Parameters**

| 파라미터     | 타입   | 필수 | 설명                               |
| ------------ | ------ | ---- | ---------------------------------- |
| `documentId` | string | ✅   | 사규 document_id (예: POL-EDU-015) |

**요청 Body**

```json
{
  "fileUrl": "s3://ctrl-s3/docs/policy_v2.pdf",
  "changeSummary": "퀴즈 리포트 및 배포 캘린더 추가"
}
```

| 필드            | 타입   | 필수 | 설명        |
| --------------- | ------ | ---- | ----------- |
| `fileUrl`       | string | ❌   | S3 파일 URL |
| `changeSummary` | string | ❌   | 변경 요약   |

**응답 (201 Created)**

```json
{
  "id": "024776fa-009b-4161-ab3b-33c974a3844b",
  "documentId": "POL-EDU-015",
  "version": 2,
  "status": "DRAFT",
  "createdAt": "2025-12-29T12:34:56Z"
}
```

**에러 응답**

- `404 Not Found`: 사규를 찾을 수 없음

---

### 3.7 버전 수정

사규 버전의 change_summary나 파일을 수정합니다.

**엔드포인트**

```
PATCH /rag/documents/policies/{documentId}/versions/{version}
```

**인증**: ❌ (명시되지 않음)

**Path Parameters**

| 파라미터     | 타입    | 필수 | 설명                               |
| ------------ | ------- | ---- | ---------------------------------- |
| `documentId` | string  | ✅   | 사규 document_id (예: POL-EDU-015) |
| `version`    | integer | ✅   | 버전 번호 (예: 2)                  |

**요청 Body**

```json
{
  "changeSummary": "퀴즈 리포트(오답 분석/재학습) 및 배포 캘린더 추가(초안)",
  "fileUrl": "s3://ctrl-s3/docs/policy_v2_updated.pdf"
}
```

| 필드            | 타입   | 필수 | 설명        |
| --------------- | ------ | ---- | ----------- |
| `changeSummary` | string | ❌   | 변경 요약   |
| `fileUrl`       | string | ❌   | S3 파일 URL |

**응답 (200 OK)**

```json
{
  "id": "024776fa-009b-4161-ab3b-33c974a3844b",
  "documentId": "POL-EDU-015",
  "version": 2,
  "status": "DRAFT",
  "updatedAt": "2025-12-29T12:34:56Z"
}
```

**에러 응답**

- `404 Not Found`: 버전을 찾을 수 없음

---

### 3.8 상태 변경

사규 버전의 상태를 변경합니다. ACTIVE로 변경 시 같은 document_id의 다른 ACTIVE 버전은 자동으로 DRAFT로 변경됩니다.

**엔드포인트**

```
PATCH /rag/documents/policies/{documentId}/versions/{version}/status
```

**인증**: ❌ (명시되지 않음)

**Path Parameters**

| 파라미터     | 타입    | 필수 | 설명                               |
| ------------ | ------- | ---- | ---------------------------------- |
| `documentId` | string  | ✅   | 사규 document_id (예: POL-EDU-015) |
| `version`    | integer | ✅   | 버전 번호 (예: 2)                  |

**요청 Body**

```json
{
  "status": "ACTIVE"
}
```

| 필드     | 타입   | 필수 | 설명                                    |
| -------- | ------ | ---- | --------------------------------------- |
| `status` | string | ✅   | 상태 (ACTIVE, DRAFT, PENDING, ARCHIVED) |

**응답 (200 OK)**

```json
{
  "id": "024776fa-009b-4161-ab3b-33c974a3844b",
  "documentId": "POL-EDU-015",
  "version": 2,
  "status": "ACTIVE",
  "updatedAt": "2025-12-29T12:34:56Z"
}
```

**에러 응답**

- `400 Bad Request`: 잘못된 상태 값
- `404 Not Found`: 버전을 찾을 수 없음

---

### 3.9 파일 업로드/교체

사규 버전의 파일을 교체합니다.

**엔드포인트**

```
PUT /rag/documents/policies/{documentId}/versions/{version}/file
```

**인증**: ❌ (명시되지 않음)

**Path Parameters**

| 파라미터     | 타입    | 필수 | 설명                               |
| ------------ | ------- | ---- | ---------------------------------- |
| `documentId` | string  | ✅   | 사규 document_id (예: POL-EDU-015) |
| `version`    | integer | ✅   | 버전 번호 (예: 2)                  |

**요청 Body**

```json
{
  "fileUrl": "s3://ctrl-s3/docs/policy_v2_new.pdf"
}
```

| 필드      | 타입   | 필수 | 설명        |
| --------- | ------ | ---- | ----------- |
| `fileUrl` | string | ✅   | S3 파일 URL |

**응답 (200 OK)**

```json
{
  "id": "024776fa-009b-4161-ab3b-33c974a3844b",
  "documentId": "POL-EDU-015",
  "version": 2,
  "sourceUrl": "s3://ctrl-s3/docs/policy_v2_new.pdf",
  "updatedAt": "2025-12-29T12:34:56Z"
}
```

**에러 응답**

- `404 Not Found`: 버전을 찾을 수 없음

---

## 4. 내부 API (AI → Backend)

### 4.1 사규 상태 업데이트 (AI → Backend)

AI 서버가 사규 문서의 임베딩 처리 상태를 업데이트합니다.

**엔드포인트**

```
PATCH /internal/rag/documents/{ragDocumentPk}/status
```

**인증**: 내부 토큰 (X-Internal-Token, 현재 검증 미구현)

**Path Parameters**

| 파라미터        | 타입 | 필수 | 설명                          |
| --------------- | ---- | ---- | ----------------------------- |
| `ragDocumentPk` | UUID | ✅   | RAG 문서의 Primary Key (UUID) |

**Headers**

| 헤더           | 타입   | 필수 | 설명               |
| -------------- | ------ | ---- | ------------------ |
| `Content-Type` | string | ✅   | `application/json` |

**요청 Body**

```json
{
  "status": "COMPLETED",
  "processedAt": "2025-12-29T12:34:56Z",
  "failReason": null,
  "version": 3,
  "documentId": "POL-EDU-015"
}
```

| 필드          | 타입    | 필수 | 설명                                      |
| ------------- | ------- | ---- | ----------------------------------------- |
| `status`      | string  | ✅   | 처리 상태 (PROCESSING, COMPLETED, FAILED) |
| `processedAt` | string  | ❌   | 처리 완료 시각 (ISO-8601)                 |
| `failReason`  | string  | ❌   | 실패 사유 (status가 FAILED인 경우)        |
| `version`     | integer | ❌   | 문서 버전 (검증용)                        |
| `documentId`  | string  | ❌   | 문서 ID (검증용)                          |

**응답 (200 OK)**

```json
{
  "id": "024776fa-009b-4161-ab3b-33c974a3844a",
  "documentId": "POL-EDU-015",
  "version": 3,
  "status": "COMPLETED",
  "processedAt": "2025-12-29T12:34:56Z",
  "updatedAt": "2025-12-29T12:34:57Z"
}
```

**에러 응답**

- `400 Bad Request`: 잘못된 요청 (예: 잘못된 status 값)
- `401 Unauthorized`: 내부 토큰 오류
- `404 Not Found`: 문서를 찾을 수 없음

---

## 공통 에러 응답 형식

### 표준 에러 응답

```json
{
  "timestamp": "2025-12-29T12:34:56Z",
  "status": 400,
  "error": "Bad Request",
  "message": "상세 에러 메시지",
  "path": "/rag/documents/policies"
}
```

---

## 상태 코드 참조

### RAG 문서 처리 상태

- `QUEUED`: 대기 중
- `PROCESSING`: 처리 중
- `COMPLETED`: 처리 완료
- `FAILED`: 처리 실패
- `REPROCESSING`: 재처리 중

### 사규 관리 상태

- `ACTIVE`: 활성
- `DRAFT`: 초안
- `PENDING`: 대기 중
- `ARCHIVED`: 보관됨

---

## 참고사항

1. **인증**: 대부분의 API는 JWT Bearer Token 인증이 필요하지만, 일부 API는 명시되지 않았습니다. 실제 구현을 확인하세요.

2. **내부 API**: 내부 API는 서비스 간 통신용이며, `X-Internal-Token` 헤더를 사용합니다. 현재는 검증 로직이 구현되지 않았습니다.

3. **페이지네이션**: `PageResponse` 형식은 다음과 같습니다:

   ```json
   {
     "items": [...],
     "page": 0,
     "size": 20,
     "total": 100
   }
   ```

4. **날짜 형식**: 모든 날짜/시간 필드는 ISO-8601 형식을 사용합니다 (예: `2025-12-29T12:34:56Z`).

5. **UUID 형식**: 모든 UUID는 하이픈 포함 형식을 사용합니다 (예: `024776fa-009b-4161-ab3b-33c974a3844a`).
