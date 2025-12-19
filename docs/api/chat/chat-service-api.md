# Chat Service API 명세서

## 목차
1. [Chat Session API](#chat-session-api)
2. [Chat Message API](#chat-message-api)
3. [Chat Stream API](#chat-stream-api)
4. [Chat Feedback API](#chat-feedback-api)
5. [FAQ API](#faq-api)
6. [FAQ Dashboard API](#faq-dashboard-api)
7. [FAQ Admin API](#faq-admin-api)
8. [AI Search Test API](#ai-search-test-api)

---

## Chat Session API

### 1. 세션 생성
**POST** `/api/chat/sessions`

세션을 생성합니다.

#### Request Body
```json
{
  "userUuid": "uuid",
  "title": "string",
  "domain": "string"
}
```

#### Response
```json
{
  "id": "uuid",
  "title": "string",
  "domain": "string",
  "userUuid": "uuid",
  "createdAt": "2024-01-01T00:00:00Z",
  "updatedAt": "2024-01-01T00:00:00Z"
}
```

---

### 2. 세션 단건 조회
**GET** `/api/chat/sessions/{sessionId}`

특정 세션의 정보를 조회합니다.

#### Path Parameters
- `sessionId` (UUID): 세션 ID

#### Response
```json
{
  "id": "uuid",
  "title": "string",
  "domain": "string",
  "userUuid": "uuid",
  "createdAt": "2024-01-01T00:00:00Z",
  "updatedAt": "2024-01-01T00:00:00Z"
}
```

---

### 3. 세션 목록 조회
**GET** `/api/chat/sessions`

모든 세션 목록을 조회합니다.

#### Response
```json
[
  {
    "id": "uuid",
    "title": "string",
    "domain": "string",
    "userUuid": "uuid",
    "createdAt": "2024-01-01T00:00:00Z",
    "updatedAt": "2024-01-01T00:00:00Z"
  }
]
```

---

### 4. 세션 수정
**PUT** `/api/chat/sessions/{sessionId}`

세션 정보를 수정합니다.

#### Path Parameters
- `sessionId` (UUID): 세션 ID

#### Request Body
```json
{
  "title": "string"
}
```

#### Response
```json
{
  "id": "uuid",
  "title": "string",
  "domain": "string",
  "userUuid": "uuid",
  "createdAt": "2024-01-01T00:00:00Z",
  "updatedAt": "2024-01-01T00:00:00Z"
}
```

---

### 5. 세션 삭제
**DELETE** `/api/chat/sessions/{sessionId}`

세션을 삭제합니다.

#### Path Parameters
- `sessionId` (UUID): 세션 ID

#### Response
- `204 No Content`

---

### 6. 세션 히스토리 조회
**GET** `/api/chat/sessions/{sessionId}/history`

세션의 전체 메시지 히스토리를 조회합니다.

#### Path Parameters
- `sessionId` (UUID): 세션 ID

#### Response
```json
{
  "sessionId": "uuid",
  "title": "string",
  "messages": [
    {
      "id": "uuid",
      "sessionId": "uuid",
      "role": "user|assistant",
      "content": "string",
      "tokensIn": 0,
      "tokensOut": 0,
      "llmModel": "string",
      "createdAt": "2024-01-01T00:00:00Z"
    }
  ]
}
```

---

## Chat Message API

### 1. 메시지 전송
**POST** `/chat/messages`

메시지를 전송하고 AI 응답을 받습니다.

#### Authentication
- JWT 토큰 필요
- `Authorization: Bearer {token}`

#### Request Body
```json
{
  "sessionId": "uuid",
  "content": "string"
}
```

#### Response
```json
{
  "messageId": "uuid",
  "role": "assistant",
  "content": "string",
  "createdAt": "2024-01-01T00:00:00Z"
}
```

---

### 2. 세션별 메시지 조회 (커서 기반)
**GET** `/chat/sessions/{sessionId}/messages`

세션의 메시지를 커서 기반 페이지네이션으로 조회합니다.

#### Path Parameters
- `sessionId` (UUID): 세션 ID

#### Query Parameters
- `cursor` (String, optional): 다음 페이지 커서
- `size` (Integer, default: 20): 페이지 크기

#### Response
```json
{
  "messages": [
    {
      "id": "uuid",
      "sessionId": "uuid",
      "role": "user|assistant",
      "content": "string",
      "tokensIn": 0,
      "tokensOut": 0,
      "llmModel": "string",
      "createdAt": "2024-01-01T00:00:00Z"
    }
  ],
  "nextCursor": "string|null",
  "hasNext": true
}
```

---

### 3. 메시지 재시도
**POST** `/chat/sessions/{sessionId}/messages/{messageId}/retry`

메시지를 재시도합니다.

#### Path Parameters
- `sessionId` (UUID): 세션 ID
- `messageId` (UUID): 메시지 ID

#### Response
```json
{
  "id": "uuid",
  "sessionId": "uuid",
  "role": "assistant",
  "content": "string",
  "tokensIn": 0,
  "tokensOut": 0,
  "llmModel": "string",
  "createdAt": "2024-01-01T00:00:00Z"
}
```

---

### 4. 메시지 재생성
**POST** `/chat/sessions/{sessionId}/messages/{messageId}/regen`

메시지를 재생성합니다.

#### Path Parameters
- `sessionId` (UUID): 세션 ID
- `messageId` (UUID): 메시지 ID

#### Response
```json
{
  "id": "uuid",
  "sessionId": "uuid",
  "role": "assistant",
  "content": "string",
  "tokensIn": 0,
  "tokensOut": 0,
  "llmModel": "string",
  "createdAt": "2024-01-01T00:00:00Z"
}
```

---

## Chat Stream API

### 1. 메시지 스트림 조회
**GET** `/chat/messages/{messageId}/stream`

메시지의 스트리밍 응답을 SSE(Server-Sent Events)로 받습니다.

#### Path Parameters
- `messageId` (UUID): 메시지 ID

#### Response
- `text/event-stream` 형식의 SSE 스트림

---

## Chat Feedback API

### 1. 세션 피드백 제출
**POST** `/chat/sessions/{sessionId}/feedback`

세션 종료 시 총평을 제출합니다.

#### Authentication
- JWT 토큰 필요
- `Authorization: Bearer {token}`

#### Path Parameters
- `sessionId` (UUID): 세션 ID

#### Request Body
```json
{
  "score": 5,
  "comment": "string"
}
```

#### Response
- `200 OK`

---

### 2. 메시지 피드백 제출
**POST** `/chat/sessions/{sessionId}/messages/{messageId}/feedback`

특정 메시지에 대한 피드백을 제출합니다.

#### Authentication
- JWT 토큰 필요
- `Authorization: Bearer {token}`

#### Path Parameters
- `sessionId` (UUID): 세션 ID
- `messageId` (UUID): 메시지 ID

#### Request Body
```json
{
  "score": 5,
  "comment": "string"
}
```

#### Response
- `200 OK`

---

## FAQ API

### 1. FAQ 생성
**POST** `/chat/faq`

FAQ를 생성합니다. (관리자용)

#### Request Body
```json
{
  "question": "string (max 500)",
  "answer": "string (max 5000)",
  "domain": "string (max 50)",
  "priority": 1
}
```

#### Response
```json
"uuid"
```

---

### 2. FAQ 수정
**PATCH** `/chat/faq/{id}`

FAQ를 수정합니다. (관리자용)

#### Path Parameters
- `id` (UUID): FAQ ID

#### Request Body
```json
{
  "question": "string (max 500, optional)",
  "answer": "string (max 5000, optional)",
  "domain": "string (max 50, optional)",
  "isActive": true,
  "priority": 1
}
```

#### Response
- `200 OK`

---

### 3. FAQ 삭제
**DELETE** `/chat/faq/{id}`

FAQ를 삭제합니다. (Soft Delete, 관리자용)

#### Path Parameters
- `id` (UUID): FAQ ID

#### Response
- `200 OK`

---

### 4. FAQ 목록 조회
**GET** `/chat/faq`

모든 FAQ 목록을 조회합니다.

#### Response
```json
[
  {
    "id": "uuid",
    "question": "string",
    "answer": "string",
    "domain": "string",
    "isActive": true,
    "priority": 1,
    "createdAt": "2024-01-01T00:00:00Z",
    "updatedAt": "2024-01-01T00:00:00Z"
  }
]
```

---

## FAQ Dashboard API

### 1. FAQ 홈 조회
**GET** `/faq/home`

도메인별 대표 FAQ 1개씩을 조회합니다.

#### Response
```json
[
  {
    "id": "uuid",
    "domain": "string",
    "question": "string",
    "answer": "string",
    "publishedAt": "2024-01-01T00:00:00Z"
  }
]
```

---

### 2. 도메인별 FAQ 조회
**GET** `/faq?domain={domain}`

특정 도메인의 FAQ TOP 10을 조회합니다.

#### Query Parameters
- `domain` (String, required): 도메인

#### Response
```json
[
  {
    "id": "uuid",
    "domain": "string",
    "question": "string",
    "answer": "string",
    "publishedAt": "2024-01-01T00:00:00Z"
  }
]
```

---

### 3. 대시보드 홈 FAQ 조회
**GET** `/faq/dashboard/home`

대시보드용 홈 FAQ를 조회합니다. (도메인별 1개)

#### Response
```json
[
  {
    "id": "uuid",
    "domain": "string",
    "question": "string",
    "answer": "string",
    "publishedAt": "2024-01-01T00:00:00Z"
  }
]
```

---

### 4. 대시보드 도메인별 FAQ 조회
**GET** `/faq/dashboard/{domain}`

대시보드용 도메인별 TOP 10 FAQ를 조회합니다.

#### Path Parameters
- `domain` (String): 도메인

#### Response
```json
[
  {
    "id": "uuid",
    "domain": "string",
    "question": "string",
    "answer": "string",
    "publishedAt": "2024-01-01T00:00:00Z"
  }
]
```

---

## FAQ Admin API

### 1. FAQ 후보 목록 조회
**GET** `/admin/faq/candidates`

FAQ 후보 목록을 조회합니다.

#### Query Parameters
- `domain` (String, optional): 도메인 필터
- `status` (String, optional): 상태 필터 (NEW / ELIGIBLE / EXCLUDED)

#### Response
```json
[
  {
    "id": "uuid",
    "canonicalQuestion": "string",
    "domain": "string",
    "questionCount7d": 0,
    "questionCount30d": 0,
    "avgIntentConfidence": 0.0,
    "piiDetected": false,
    "scoreCandidate": 0.0,
    "status": "NEW|ELIGIBLE|EXCLUDED",
    "lastAskedAt": "2024-01-01T00:00:00Z",
    "createdAt": "2024-01-01T00:00:00Z"
  }
]
```

---

### 2. FAQ 후보에서 Draft 생성
**POST** `/admin/faq/candidates/{candidateId}/generate`

FAQ 후보를 기반으로 AI Draft를 생성합니다.

#### Path Parameters
- `candidateId` (UUID): 후보 ID

#### Response
```json
{
  "draftId": "uuid"
}
```

---

### 3. FAQ Draft 목록 조회
**GET** `/admin/faq/drafts`

FAQ Draft 목록을 조회합니다.

#### Query Parameters
- `domain` (String, optional): 도메인 필터
- `status` (String, optional): 상태 필터

#### Response
```json
[
  {
    "id": "uuid",
    "domain": "string",
    "question": "string",
    "summary": "string",
    "status": "PENDING|APPROVED|REJECTED",
    "createdAt": "2024-01-01T00:00:00Z"
  }
]
```

---

### 4. FAQ Draft 승인
**POST** `/admin/faq/drafts/{draftId}/approve`

FAQ Draft를 승인합니다.

#### Path Parameters
- `draftId` (UUID): Draft ID

#### Query Parameters
- `reviewerId` (UUID, required): 리뷰어 ID
- `question` (String, required): 최종 질문
- `answer` (String, required): 최종 답변

#### Response
- `200 OK`

---

### 5. FAQ Draft 반려
**POST** `/admin/faq/drafts/{draftId}/reject`

FAQ Draft를 반려합니다.

#### Path Parameters
- `draftId` (UUID): Draft ID

#### Query Parameters
- `reviewerId` (UUID, required): 리뷰어 ID
- `reason` (String, required): 반려 사유

#### Response
- `200 OK`

---

### 6. FAQ 후보에서 Draft 생성 (간편)
**POST** `/admin/faqs/candidates/{id}/generate`

FAQ 후보를 기반으로 Draft를 생성합니다. (간편 버전)

#### Path Parameters
- `id` (UUID): 후보 ID

#### Response
```json
"uuid"
```

---

### 7. FAQ Draft 승인 (간편)
**POST** `/admin/faqs/drafts/{id}/approve`

FAQ Draft를 승인합니다. (간편 버전)

#### Path Parameters
- `id` (UUID): Draft ID

#### Query Parameters
- `reviewerId` (UUID, required): 리뷰어 ID
- `question` (String, required): 최종 질문
- `answer` (String, required): 최종 답변

#### Response
- `200 OK`

---

### 8. FAQ Draft 반려 (간편)
**POST** `/admin/faqs/drafts/{id}/reject`

FAQ Draft를 반려합니다. (간편 버전)

#### Path Parameters
- `id` (UUID): Draft ID

#### Query Parameters
- `reviewerId` (UUID, required): 리뷰어 ID
- `reason` (String, required): 반려 사유

#### Response
- `200 OK`

---

### 9. FAQ UI 카테고리 생성
**POST** `/admin/faq/ui-categories?operatorId={operatorId}`

FAQ UI 카테고리를 생성합니다.

#### Query Parameters
- `operatorId` (UUID, required): 운영자 ID

#### Request Body
```json
{
  "slug": "string",
  "displayName": "string",
  "sortOrder": 0
}
```

#### Response
```json
"uuid"
```

---

### 10. FAQ UI 카테고리 수정
**PATCH** `/admin/faq/ui-categories/{categoryId}?operatorId={operatorId}`

FAQ UI 카테고리를 수정합니다.

#### Path Parameters
- `categoryId` (UUID): 카테고리 ID

#### Query Parameters
- `operatorId` (UUID, required): 운영자 ID

#### Request Body
```json
{
  "displayName": "string",
  "sortOrder": 0,
  "isActive": true
}
```

#### Response
- `200 OK`

---

### 11. FAQ UI 카테고리 비활성화
**POST** `/admin/faq/ui-categories/{categoryId}/deactivate?operatorId={operatorId}`

FAQ UI 카테고리를 비활성화합니다.

#### Path Parameters
- `categoryId` (UUID): 카테고리 ID

#### Query Parameters
- `operatorId` (UUID, required): 운영자 ID
- `reason` (String, optional): 비활성화 사유

#### Response
- `200 OK`

---

### 12. FAQ UI 카테고리 목록 조회
**GET** `/admin/faq/ui-categories`

모든 FAQ UI 카테고리 목록을 조회합니다.

#### Response
```json
[
  {
    "id": "uuid",
    "slug": "string",
    "displayName": "string",
    "sortOrder": 0,
    "isActive": true,
    "createdAt": "2024-01-01T00:00:00Z",
    "updatedAt": "2024-01-01T00:00:00Z"
  }
]
```

---

### 13. FAQ Seed CSV 업로드
**POST** `/admin/faq/seed/upload?operatorId={operatorId}`

FAQ 초기 데이터를 CSV 파일로 업로드합니다.

#### Query Parameters
- `operatorId` (UUID, required): 운영자 ID

#### Request Body
- `multipart/form-data`
- `file` (MultipartFile, required): CSV 파일

#### Response
- `200 OK`

---

## AI Search Test API

### 1. AI 검색 테스트
**POST** `/internal/ai/search`

⚠️ 테스트용 API (추후 삭제 가능)

#### Query Parameters
- `query` (String, required): 검색 쿼리
- `dataset` (String, default: "policy"): 데이터셋
- `topK` (Integer, default: 5): 상위 K개 결과

#### Response
```json
[
  {
    "documentId": "string",
    "score": 0.0,
    "content": "string"
  }
]
```

---

## 공통 사항

### 인증
대부분의 API는 JWT 토큰을 사용한 인증이 필요합니다.
- Header: `Authorization: Bearer {token}`
- JWT 토큰은 Keycloak에서 발급됩니다.

### 에러 응답
에러 발생 시 다음과 같은 형식으로 응답됩니다:
```json
{
  "error": "string",
  "message": "string",
  "status": 400
}
```

### 데이터 타입
- `UUID`: UUID 형식의 문자열
- `Instant`: ISO 8601 형식의 날짜/시간 문자열 (예: "2024-01-01T00:00:00Z")
- `String`: 문자열
- `Integer`: 정수
- `Boolean`: 불린 값

### 도메인 값
도메인은 다음과 같은 값들을 사용할 수 있습니다:
- `SECURITY`
- `POLICY`
- `EDUCATION`
- 기타 정의된 도메인 값

---

## 참고사항

- 모든 시간은 UTC 기준입니다.
- UUID는 표준 UUID v4 형식을 사용합니다.
- 페이지네이션은 커서 기반 방식을 사용합니다.
- FAQ의 우선순위는 1~5 사이의 값이며, 작을수록 상위 노출됩니다.


