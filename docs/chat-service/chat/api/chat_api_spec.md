# Chat Service API 명세서

## 개요

Chat Service는 사용자와 AI 간의 채팅 세션 및 메시지 관리를 담당하는 서비스입니다.

- **Base URL**: `http://localhost:9005`
- **인증**: Bearer Token (JWT)

---

## 1. Chat Session APIs

### 1.1 세션 생성

새로운 채팅 세션을 생성합니다.

**Endpoint**: `POST /api/chat/sessions`

**Request Body**:
```json
{
  "userUuid": "076d9ad4-a3b8-4853-95fe-7c427c8bc529",
  "title": "세션 제목",
  "domain": "SECURITY"
}
```

**Response** (200 OK):
```json
{
  "id": "239d0429-b517-4897-beb0-bd1f699999da",
  "title": "세션 제목",
  "domain": "SECURITY",
  "userUuid": "076d9ad4-a3b8-4853-95fe-7c427c8bc529",
  "createdAt": "2025-12-19T23:00:00Z",
  "updatedAt": "2025-12-19T23:00:00Z"
}
```

---

### 1.2 세션 단건 조회

특정 세션의 상세 정보를 조회합니다.

**Endpoint**: `GET /api/chat/sessions/{sessionId}`

**Path Parameters**:
- `sessionId` (UUID): 조회할 세션 ID

**Response** (200 OK):
```json
{
  "id": "239d0429-b517-4897-beb0-bd1f699999da",
  "title": "세션 제목",
  "domain": "SECURITY",
  "userUuid": "076d9ad4-a3b8-4853-95fe-7c427c8bc529",
  "createdAt": "2025-12-19T23:00:00Z",
  "updatedAt": "2025-12-19T23:00:00Z"
}
```

---

### 1.3 세션 목록 조회

모든 세션 목록을 조회합니다.

**Endpoint**: `GET /api/chat/sessions`

**Response** (200 OK):
```json
[
  {
    "id": "239d0429-b517-4897-beb0-bd1f699999da",
    "title": "세션 제목",
    "domain": "SECURITY",
    "userUuid": "076d9ad4-a3b8-4853-95fe-7c427c8bc529",
    "createdAt": "2025-12-19T23:00:00Z",
    "updatedAt": "2025-12-19T23:00:00Z"
  }
]
```

---

### 1.4 세션 수정

세션의 제목 등의 정보를 수정합니다.

**Endpoint**: `PUT /api/chat/sessions/{sessionId}`

**Path Parameters**:
- `sessionId` (UUID): 수정할 세션 ID

**Request Body**:
```json
{
  "title": "수정된 제목"
}
```

**Response** (200 OK):
```json
{
  "id": "239d0429-b517-4897-beb0-bd1f699999da",
  "title": "수정된 제목",
  "domain": "SECURITY",
  "userUuid": "076d9ad4-a3b8-4853-95fe-7c427c8bc529",
  "createdAt": "2025-12-19T23:00:00Z",
  "updatedAt": "2025-12-19T23:01:00Z"
}
```

---

### 1.5 세션 삭제 (Soft Delete)

세션을 삭제합니다. 실제로는 deleted 플래그를 true로 설정하는 소프트 삭제입니다.

**Endpoint**: `DELETE /api/chat/sessions/{sessionId}`

**Path Parameters**:
- `sessionId` (UUID): 삭제할 세션 ID

**Response** (200 OK): No Content

---

### 1.6 세션 히스토리 조회

세션의 전체 메시지 히스토리를 조회합니다.

**Endpoint**: `GET /api/chat/sessions/{sessionId}/history`

**Path Parameters**:
- `sessionId` (UUID): 조회할 세션 ID

**Response** (200 OK):
```json
{
  "sessionId": "239d0429-b517-4897-beb0-bd1f699999da",
  "title": "세션 제목",
  "messages": [
    {
      "id": "46a5fc72-4b1f-48ac-a65b-6bd16f0fcb5f",
      "sessionId": "239d0429-b517-4897-beb0-bd1f699999da",
      "role": "user",
      "content": "안녕하세요",
      "tokensIn": null,
      "tokensOut": null,
      "llmModel": null,
      "createdAt": "2025-12-19T23:00:00Z"
    },
    {
      "id": "46a5fc72-4b1f-48ac-a65b-6bd16f0fcb5f",
      "sessionId": "239d0429-b517-4897-beb0-bd1f699999da",
      "role": "assistant",
      "content": "안녕하세요! 무엇을 도와드릴까요?",
      "tokensIn": 10,
      "tokensOut": 15,
      "llmModel": "gpt-4",
      "createdAt": "2025-12-19T23:00:01Z"
    }
  ]
}
```

---

## 2. Chat Message APIs

### 2.1 메시지 전송 및 AI 응답 생성

사용자가 메시지를 전송하면 AI 서버에 요청을 보내고 응답을 받아 저장합니다.

**Endpoint**: `POST /chat/messages`

**인증**: Bearer Token (JWT) - `domain` 클레임 필요

**Request Body**:
```json
{
  "sessionId": "239d0429-b517-4897-beb0-bd1f699999da",
  "content": "안녕하세요"
}
```

**Response** (200 OK):
```json
{
  "messageId": "46a5fc72-4b1f-48ac-a65b-6bd16f0fcb5f",
  "role": "assistant",
  "content": "안녕하세요! 무엇을 도와드릴까요?",
  "createdAt": "2025-12-19T23:00:01Z"
}
```

**참고**: 
- 사용자 메시지와 AI 응답 메시지가 모두 저장됩니다.
- 응답은 생성된 AI 응답 메시지 정보를 반환합니다.

---

### 2.2 세션별 메시지 목록 조회 (커서 기반 페이지네이션)

특정 세션의 메시지 목록을 커서 기반 페이지네이션으로 조회합니다.

**Endpoint**: `GET /chat/sessions/{sessionId}/messages`

**Path Parameters**:
- `sessionId` (UUID): 세션 ID

**Query Parameters**:
- `cursor` (String, optional): 다음 페이지 커서 (첫 페이지 조회 시 생략)
- `size` (int, optional): 페이지 크기 (기본값: 20, 최대: 100)

**Response** (200 OK):
```json
{
  "messages": [
    {
      "id": "46a5fc72-4b1f-48ac-a65b-6bd16f0fcb5f",
      "sessionId": "239d0429-b517-4897-beb0-bd1f699999da",
      "role": "user",
      "content": "안녕하세요",
      "tokensIn": null,
      "tokensOut": null,
      "llmModel": null,
      "createdAt": "2025-12-19T23:00:00Z"
    }
  ],
  "nextCursor": "1734567890000_46a5fc72-4b1f-48ac-a65b-6bd16f0fcb5f",
  "hasNext": true
}
```

**커서 형식**: `{timestamp}_{messageId}` (예: `1734567890000_46a5fc72-4b1f-48ac-a65b-6bd16f0fcb5f`)

---

### 2.3 메시지 재시도

실패한 메시지에 대해 AI 서버에 재요청을 보냅니다.

**Endpoint**: `POST /chat/sessions/{sessionId}/messages/{messageId}/retry`

**Path Parameters**:
- `sessionId` (UUID): 세션 ID
- `messageId` (UUID): 재시도할 메시지 ID (assistant 메시지여야 함)

**Response** (200 OK):
```json
{
  "id": "46a5fc72-4b1f-48ac-a65b-6bd16f0fcb5f",
  "sessionId": "239d0429-b517-4897-beb0-bd1f699999da",
  "role": "assistant",
  "content": "재시도된 응답 내용",
  "tokensIn": 10,
  "tokensOut": 15,
  "llmModel": "gpt-4",
  "createdAt": "2025-12-19T23:00:01Z"
}
```

**참고**: 
- 재시도는 assistant 메시지에 대해서만 가능합니다.
- 재시도 시 기존 메시지의 내용이 업데이트됩니다.

---

## 3. Chat Message Stream API (SSE)

### 3.1 메시지 스트림 조회 (SSE)

특정 메시지의 AI 응답을 실시간으로 스트리밍합니다. SSE(Server-Sent Events) 프로토콜을 사용합니다.

**Endpoint**: `GET /chat/messages/{messageId}/stream`

**Path Parameters**:
- `messageId` (UUID): 스트리밍할 메시지 ID

**Headers**:
- `Accept: text/event-stream`

**Response** (200 OK): SSE 스트림

**이벤트 타입**:
- `meta`: 스트림 시작 메타데이터
- `token`: AI 응답 토큰 (실시간)
- `done`: 스트림 완료
- `error`: 에러 발생

**예시**:
```
event:meta
data:stream-start:46a5fc72-4b1f-48ac-a65b-6bd16f0fcb5f

event:token
data:안녕

event:token
data:하세요

event:done
data:END
```

---

## 4. Chat Feedback APIs

### 4.1 메시지 피드백 제출

특정 메시지에 대한 사용자 피드백(평점, 코멘트)을 저장합니다.

**Endpoint**: `POST /chat/sessions/{sessionId}/messages/{messageId}/feedback`

**인증**: Bearer Token (JWT)

**Path Parameters**:
- `sessionId` (UUID): 세션 ID
- `messageId` (UUID): 피드백을 제출할 메시지 ID

**Request Body**:
```json
{
  "score": 5,
  "comment": "좋은 답변입니다"
}
```

**Response** (200 OK): No Content

---

### 4.2 세션 피드백 제출

세션 종료 시 전체 세션에 대한 사용자 피드백(평점, 코멘트)을 저장합니다.

**Endpoint**: `POST /chat/sessions/{sessionId}/feedback`

**인증**: Bearer Token (JWT)

**Path Parameters**:
- `sessionId` (UUID): 피드백을 제출할 세션 ID

**Request Body**:
```json
{
  "score": 5,
  "comment": "전체적으로 만족합니다"
}
```

**Response** (200 OK): No Content

---

## 5. Chat Admin Dashboard APIs

### 5.1 대시보드 요약 통계 조회

오늘 질문 수, 평균 응답 시간, PII 감지 비율, 에러율, 최근 7일 질문 수, 활성 사용자 수, 응답 만족도, RAG 사용 비율을 조회합니다.

**Endpoint**: `GET /admin/dashboard/summary`

**인증**: Bearer Token (JWT) - ADMIN 권한 필요

**Query Parameters**:
- `period` (Integer, 선택): 기간 (일수, 7/30/90, 기본값: 30)
- `department` (String, 선택): 부서 필터

**Response** (200 OK):
```json
{
  "todayQuestionCount": 150,
  "averageResponseTime": 1250,
  "piiDetectionRate": 2.5,
  "errorRate": 1.2,
  "last7DaysQuestionCount": 1050,
  "activeUserCount": 85,
  "satisfactionRate": 92.5,
  "ragUsageRate": 65.3
}
```

---

### 5.2 라우트별 질문 비율 조회

RAG, LLM, Incident, FAQ, 기타 라우트별 질문 비율을 조회합니다.

**Endpoint**: `GET /admin/dashboard/route-ratio`

**인증**: Bearer Token (JWT) - ADMIN 권한 필요

**Query Parameters**:
- `period` (Integer, 선택): 기간 (일수, 7/30/90, 기본값: 30)
- `department` (String, 선택): 부서 필터

**Response** (200 OK):
```json
{
  "items": [
    {
      "routeType": "RAG",
      "routeName": "RAG 검색",
      "ratio": 65.3
    },
    {
      "routeType": "LLM",
      "routeName": "LLM 직접 응답",
      "ratio": 20.5
    },
    {
      "routeType": "FAQ",
      "routeName": "FAQ 매칭",
      "ratio": 10.2
    },
    {
      "routeType": "INCIDENT",
      "routeName": "사고 처리",
      "ratio": 3.0
    },
    {
      "routeType": "OTHER",
      "routeName": "기타",
      "ratio": 1.0
    }
  ]
}
```

---

### 5.3 최근 많이 질문된 키워드 Top 5 조회

최근 많이 질문된 키워드와 질문 횟수를 조회합니다.

**Endpoint**: `GET /admin/dashboard/top-keywords`

**인증**: Bearer Token (JWT) - ADMIN 권한 필요

**Query Parameters**:
- `period` (Integer, 선택): 기간 (일수, 7/30/90, 기본값: 30)
- `department` (String, 선택): 부서 필터

**Response** (200 OK):
```json
{
  "items": [
    {
      "keyword": "비밀번호",
      "questionCount": 45
    },
    {
      "keyword": "휴가 신청",
      "questionCount": 32
    },
    {
      "keyword": "급여 조회",
      "questionCount": 28
    },
    {
      "keyword": "보안 정책",
      "questionCount": 25
    },
    {
      "keyword": "출근 시간",
      "questionCount": 20
    }
  ]
}
```

---

### 5.4 질문 수 · 에러율 추이 조회

주간별 질문 수와 에러율 추이를 조회합니다.

**Endpoint**: `GET /admin/dashboard/question-trend`

**인증**: Bearer Token (JWT) - ADMIN 권한 필요

**Query Parameters**:
- `period` (Integer, 선택): 기간 (일수, 7/30/90, 기본값: 30)
- `department` (String, 선택): 부서 필터

**Response** (200 OK):
```json
{
  "totalQuestionCount": 1050,
  "averageQuestionCountPerPeriod": 150,
  "averageErrorRate": 1.2,
  "items": [
    {
      "periodLabel": "1주",
      "questionCount": 145,
      "errorRate": 1.0
    },
    {
      "periodLabel": "2주",
      "questionCount": 152,
      "errorRate": 1.2
    },
    {
      "periodLabel": "3주",
      "questionCount": 148,
      "errorRate": 1.3
    },
    {
      "periodLabel": "4주",
      "questionCount": 155,
      "errorRate": 1.1
    }
  ]
}
```

---

### 5.5 도메인별 질문 비율 조회

규정, FAQ, 교육, 퀴즈, 기타 도메인별 질문 비율을 조회합니다.

**Endpoint**: `GET /admin/dashboard/domain-ratio`

**인증**: Bearer Token (JWT) - ADMIN 권한 필요

**Query Parameters**:
- `period` (Integer, 선택): 기간 (일수, 7/30/90, 기본값: 30)
- `department` (String, 선택): 부서 필터

**Response** (200 OK):
```json
{
  "items": [
    {
      "domain": "SECURITY",
      "domainName": "보안",
      "ratio": 35.5
    },
    {
      "domain": "POLICY",
      "domainName": "정책",
      "ratio": 25.3
    },
    {
      "domain": "EDUCATION",
      "domainName": "교육",
      "ratio": 20.2
    },
    {
      "domain": "QUIZ",
      "domainName": "퀴즈",
      "ratio": 15.0
    },
    {
      "domain": "OTHER",
      "domainName": "기타",
      "ratio": 4.0
    }
  ]
}
```

---

## 에러 응답

### 공통 에러 형식

```json
{
  "error": "ERROR_TYPE",
  "message": "에러 메시지",
  "timestamp": "2025-12-19T23:00:00Z",
  "status": 400
}
```

### 주요 HTTP 상태 코드

- `200 OK`: 성공
- `400 Bad Request`: 잘못된 요청
- `401 Unauthorized`: 인증 실패
- `404 Not Found`: 리소스를 찾을 수 없음
- `500 Internal Server Error`: 서버 오류

---

## 내부 API (Chat Service → AI Server)

Chat Service는 AI Server와 통신하기 위해 다음 내부 API를 사용합니다:

| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| POST | `/ai/chat/messages` | 일반 채팅 응답 요청 |
| POST | `/ai/chat/stream` | 스트리밍 채팅 응답 요청 (NDJSON) |

**참고**: 
- `/ai/search` 엔드포인트는 더 이상 사용되지 않습니다. (삭제됨)
- RAG 검색은 AI Server 내부에서 자동으로 처리됩니다.

---

## 참고사항

- 모든 UUID는 표준 UUID 형식입니다.
- 모든 날짜/시간은 ISO 8601 형식 (UTC)입니다.
- 인증이 필요한 API는 `Authorization: Bearer {token}` 헤더를 포함해야 합니다.
- SSE 스트림은 연결이 유지되는 동안 지속적으로 데이터를 전송합니다.
