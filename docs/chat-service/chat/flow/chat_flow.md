# 채팅 서비스 플로우

## 개요

Chat Service는 사용자와 AI 간의 실시간 대화를 관리하는 서비스입니다. 사용자가 질문을 입력하면 AI 서버를 통해 RAG(Retrieval-Augmented Generation) 기반 답변을 생성하고, 대화 히스토리를 관리합니다.

## 엔티티 구조

```
ChatSession (채팅 세션)
├── id: UUID
├── userUuid: 사용자 UUID
├── title: 세션 제목
├── domain: 도메인 (SECURITY, POLICY, EDUCATION 등)
├── createdAt: 생성 시간
└── updatedAt: 수정 시간
    │
    └── ChatMessage (채팅 메시지)
        ├── id: UUID
        ├── sessionId: 세션 ID
        ├── role: 역할 (user, assistant)
        ├── content: 메시지 내용
        ├── tokensIn: 입력 토큰 수
        ├── tokensOut: 출력 토큰 수
        ├── llmModel: 사용된 LLM 모델
        ├── sources: RAG 검색 소스 (JSON)
        └── createdAt: 생성 시간
```

## 상태 흐름

### ChatSession 생명주기

```
생성 → 활성 → 종료 (Soft Delete)
  │      │       │
  │      │       └── deletedAt 설정
  │      └── 메시지 주고받기
  └── 세션 생성
```

### ChatMessage 역할

```
user → assistant → user → assistant → ...
  │        │         │        │
  │        │         │        └── AI 응답
  │        │         └── 사용자 질문
  │        └── AI 응답
  └── 사용자 질문
```

## 상세 플로우

### 1단계: 채팅 세션 생성

**액션**: 사용자가 "새 채팅 시작" 버튼 클릭

**API**: `POST /api/chat/sessions` (chat-service)

**요청**:
```json
{
  "userUuid": "076d9ad4-a3b8-4853-95fe-7c427c8bc529",
  "title": "보안 관련 질문",
  "domain": "SECURITY"
}
```

**결과**:
- `ChatSession` 생성
- `sessionId` 반환

**응답**:
```json
{
  "id": "239d0429-b517-4897-beb0-bd1f699999da",
  "title": "보안 관련 질문",
  "domain": "SECURITY",
  "userUuid": "076d9ad4-a3b8-4853-95fe-7c427c8bc529",
  "createdAt": "2025-12-24T02:00:00Z",
  "updatedAt": "2025-12-24T02:00:00Z"
}
```

---

### 2단계: 메시지 전송 및 AI 응답 생성

**액션**: 사용자가 메시지 입력 후 전송

**API**: `POST /chat/messages` (chat-service)

**요청**:
```json
{
  "sessionId": "239d0429-b517-4897-beb0-bd1f699999da",
  "content": "비밀번호를 잊어버렸어요"
}
```

**처리 과정**:

1. **사용자 메시지 저장**
   - `ChatMessage` 생성 (`role: user`)
   - 데이터베이스에 저장

2. **AI 서버 호출**
   - `POST /ai/chat/messages` (AI Server)
   - 요청 본문:
     ```json
     {
       "session_id": "239d0429-b517-4897-beb0-bd1f699999da",
       "user_id": "076d9ad4-a3b8-4853-95fe-7c427c8bc529",
       "user_role": "USER",
       "domain": "SECURITY",
       "messages": [
         {
           "role": "user",
           "content": "비밀번호를 잊어버렸어요"
         }
       ]
     }
     ```

3. **AI 서버 내부 처리**
   - RAGFlow 호출: 질문 임베딩 및 벡터 검색
   - Milvus (Vector DB)에서 관련 문서 청크 검색
   - LLM 호출: 컨텍스트 + 질문 → 답변 생성

4. **AI 응답 저장**
   - `ChatMessage` 생성 (`role: assistant`)
   - `tokensIn`, `tokensOut`, `llmModel`, `sources` 저장

**응답**:
```json
{
  "messageId": "46a5fc72-4b1f-48ac-a65b-6bd16f0fcb5f",
  "role": "assistant",
  "content": "비밀번호 재설정 페이지에서 이메일을 입력하시면 재설정 링크를 보내드립니다.",
  "createdAt": "2025-12-24T02:00:01Z"
}
```

---

### 3단계: 스트리밍 응답 (SSE) - 선택적

**액션**: 사용자가 실시간 스트리밍 응답 요청

**API**: `GET /chat/messages/{messageId}/stream` (chat-service)

**처리 과정**:

1. **SSE 연결 시작**
   - `Accept: text/event-stream` 헤더 필요
   - 연결 유지

2. **AI 서버 스트리밍 호출**
   - `POST /ai/chat/stream` (AI Server)
   - NDJSON 스트림 응답

3. **실시간 토큰 전송**
   ```
   event:meta
   data:stream-start:46a5fc72-4b1f-48ac-a65b-6bd16f0fcb5f
   
   event:token
   data:비밀번호
   
   event:token
   data: 재설정
   
   event:done
   data:END
   ```

4. **스트림 완료 후 저장**
   - 전체 응답을 데이터베이스에 저장

---

### 4단계: 메시지 재시도

**액션**: 사용자가 실패한 메시지 재시도

**API**: `POST /chat/sessions/{sessionId}/messages/{messageId}/retry` (chat-service)

**처리 과정**:

1. **이전 메시지 조회**
   - 사용자 메시지와 이전 assistant 메시지 조회

2. **AI 서버 재호출**
   - 동일한 요청을 AI 서버에 재전송

3. **메시지 업데이트**
   - 기존 assistant 메시지 내용 업데이트
   - 또는 새 메시지 생성

**응답**:
```json
{
  "id": "46a5fc72-4b1f-48ac-a65b-6bd16f0fcb5f",
  "sessionId": "239d0429-b517-4897-beb0-bd1f699999da",
  "role": "assistant",
  "content": "재시도된 응답 내용",
  "tokensIn": 10,
  "tokensOut": 15,
  "llmModel": "gpt-4",
  "createdAt": "2025-12-24T02:00:01Z"
}
```

---

### 5단계: 세션 히스토리 조회

**액션**: 사용자가 이전 대화 내역 조회

**API**: `GET /api/chat/sessions/{sessionId}/history` (chat-service)

**응답**:
```json
{
  "sessionId": "239d0429-b517-4897-beb0-bd1f699999da",
  "title": "보안 관련 질문",
  "messages": [
    {
      "id": "46a5fc72-4b1f-48ac-a65b-6bd16f0fcb5f",
      "sessionId": "239d0429-b517-4897-beb0-bd1f699999da",
      "role": "user",
      "content": "비밀번호를 잊어버렸어요",
      "createdAt": "2025-12-24T02:00:00Z"
    },
    {
      "id": "46a5fc72-4b1f-48ac-a65b-6bd16f0fcb5f",
      "sessionId": "239d0429-b517-4897-beb0-bd1f699999da",
      "role": "assistant",
      "content": "비밀번호 재설정 페이지에서 이메일을 입력하시면 재설정 링크를 보내드립니다.",
      "tokensIn": 10,
      "tokensOut": 15,
      "llmModel": "gpt-4",
      "createdAt": "2025-12-24T02:00:01Z"
    }
  ]
}
```

---

### 6단계: 피드백 제출

**액션**: 사용자가 메시지 또는 세션에 피드백 제출

#### 6-1. 메시지 피드백

**API**: `POST /chat/sessions/{sessionId}/messages/{messageId}/feedback` (chat-service)

**요청**:
```json
{
  "type": "POSITIVE",
  "comment": "매우 유용합니다."
}
```

**피드백 타입**:
- `POSITIVE`: 긍정적 피드백
- `NEGATIVE`: 부정적 피드백

#### 6-2. 세션 피드백

**API**: `POST /chat/sessions/{sessionId}/feedback` (chat-service)

**요청**:
```json
{
  "type": "POSITIVE",
  "comment": "세션이 매우 유용했습니다."
}
```

---

### 7단계: 세션 수정 및 삭제

#### 7-1. 세션 수정

**API**: `PUT /api/chat/sessions/{sessionId}` (chat-service)

**요청**:
```json
{
  "title": "수정된 제목"
}
```

#### 7-2. 세션 삭제 (Soft Delete)

**API**: `DELETE /api/chat/sessions/{sessionId}` (chat-service)

**처리**:
- `deletedAt` 필드 설정
- 실제 데이터는 유지 (소프트 삭제)

---

## 에러 처리

### AI 서버 연결 실패

**상황**: AI 서버가 응답하지 않거나 타임아웃

**처리**:
- 사용자 메시지는 저장됨
- AI 응답 메시지 생성 실패
- 에러 응답 반환: `500 Internal Server Error`

**응답 예시**:
```json
{
  "error": "INTERNAL_SERVER_ERROR",
  "message": "AI 서비스에 일시적인 문제가 발생했습니다. 잠시 후 다시 시도해 주세요.",
  "timestamp": "2025-12-24T02:00:00Z",
  "status": 500
}
```

### RAGFlow 검색 실패

**상황**: RAGFlow 서버 연결 실패 또는 검색 결과 없음

**처리**:
- AI 서버에서 처리
- `NO_DOCS_FOUND` 오류 반환
- 또는 빈 컨텍스트로 LLM 호출

### 세션/메시지 조회 실패

**상황**: 존재하지 않는 세션 또는 메시지 조회

**처리**:
- `404 Not Found` 응답
- 에러 메시지 반환

---

## 주요 엔드포인트 요약

### Chat Service (Backend)

| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| POST | `/api/chat/sessions` | 세션 생성 |
| GET | `/api/chat/sessions` | 세션 목록 조회 |
| GET | `/api/chat/sessions/{sessionId}` | 세션 단건 조회 |
| PUT | `/api/chat/sessions/{sessionId}` | 세션 수정 |
| DELETE | `/api/chat/sessions/{sessionId}` | 세션 삭제 |
| GET | `/api/chat/sessions/{sessionId}/history` | 세션 히스토리 조회 |
| POST | `/chat/messages` | 메시지 전송 |
| GET | `/chat/sessions/{sessionId}/messages` | 메시지 목록 조회 |
| GET | `/chat/messages/{messageId}/stream` | SSE 스트리밍 |
| POST | `/chat/sessions/{sessionId}/messages/{messageId}/retry` | 메시지 재시도 |
| POST | `/chat/sessions/{sessionId}/messages/{messageId}/feedback` | 메시지 피드백 |
| POST | `/chat/sessions/{sessionId}/feedback` | 세션 피드백 |

### AI Server (Gateway)

| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| POST | `/ai/chat/messages` | 일반 채팅 응답 |
| POST | `/ai/chat/stream` | 스트리밍 채팅 응답 (NDJSON) |

**참고**: `/ai/search` 엔드포인트는 더 이상 사용되지 않습니다. (삭제됨)

---

## 데이터 흐름 다이어그램

```
[사용자] 
   │
   ├─→ [Chat Service] 
   │      │
   │      ├─→ [Database] (세션/메시지 저장)
   │      │
   │      └─→ [AI Server]
   │             │
   │             ├─→ [RAGFlow] → [Milvus] (벡터 검색)
   │             │
   │             └─→ [LLM Service] (답변 생성)
   │                    │
   │                    └─→ [Chat Service] → [Database] → [사용자]
```

---

## 참고사항

- 모든 UUID는 표준 UUID 형식입니다.
- 모든 날짜/시간은 ISO 8601 형식 (UTC)입니다.
- 인증이 필요한 API는 `Authorization: Bearer {token}` 헤더를 포함해야 합니다.
- SSE 스트림은 연결이 유지되는 동안 지속적으로 데이터를 전송합니다.
- RAGFlow 서버가 실행 중이 아니면 검색 결과가 없을 수 있습니다.

---

**문서 버전**: 2025-12-24  
**작성자**: AI Assistant

