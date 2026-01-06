# 채팅 서비스 플로우 시퀀스 다이어그램

> **Notion 사용법**: 아래 Mermaid 코드를 Notion의 Code Block에서 `mermaid` 언어로 선택하여 붙여넣으면 시퀀스 다이어그램이 렌더링됩니다.

---

## 1. 일반 채팅 메시지 플로우

```mermaid
sequenceDiagram
    participant User as 사용자 (Frontend)
    participant ChatService as Chat Service<br/>(Backend)
    participant DB as Database
    participant AIServer as AI Server<br/>(Gateway)
    participant Milvus as Milvus<br/>(Vector DB)
    participant LLM as LLM Service

    Note over User,LLM: 1. 세션 생성
    User->>ChatService: POST /api/chat/sessions<br/>{userUuid, title, domain}
    ChatService->>DB: 세션 저장
    DB-->>ChatService: 세션 정보
    ChatService-->>User: 세션 생성 완료<br/>{sessionId, title, domain}

    Note over User,LLM: 2. 메시지 전송 및 AI 응답
    User->>ChatService: POST /chat/messages<br/>{sessionId, content}
    ChatService->>DB: 사용자 메시지 저장
    DB-->>ChatService: 메시지 ID

    ChatService->>AIServer: POST /ai/chat/messages<br/>{session_id, user_id, user_role,<br/>domain, messages}

    AIServer->>Milvus: RAG 검색<br/>(질문 임베딩)
    Milvus-->>AIServer: 관련 문서 청크들

    AIServer->>LLM: LLM 호출<br/>(컨텍스트 + 질문)
    LLM-->>AIServer: AI 응답 생성

    AIServer-->>ChatService: 응답<br/>{answer, sources, meta}

    ChatService->>DB: AI 응답 메시지 저장<br/>{content, tokensIn, tokensOut,<br/>llmModel, sources}
    DB-->>ChatService: 메시지 ID

    ChatService-->>User: AI 응답<br/>{messageId, role, content, createdAt}

    Note over User,LLM: 3. 히스토리 조회
    User->>ChatService: GET /api/chat/sessions/{sessionId}/history
    ChatService->>DB: 세션 메시지 조회
    DB-->>ChatService: 메시지 목록
    ChatService-->>User: 전체 대화 히스토리
```

---

## 2. 스트리밍 채팅 메시지 플로우 (SSE)

```mermaid
sequenceDiagram
    participant User as 사용자 (Frontend)
    participant ChatService as Chat Service<br/>(Backend)
    participant DB as Database
    participant AIServer as AI Server<br/>(Gateway)
    participant Milvus as Milvus<br/>(Vector DB)
    participant LLM as LLM Service

    Note over User,LLM: 1. 메시지 전송 및 스트리밍 시작
    User->>ChatService: POST /chat/messages<br/>{sessionId, content}
    ChatService->>DB: 사용자 메시지 저장
    DB-->>ChatService: 메시지 ID

    User->>ChatService: GET /chat/messages/{messageId}/stream<br/>(SSE 연결)

    ChatService->>AIServer: POST /ai/chat/stream<br/>{session_id, user_id, messages}

    AIServer->>Milvus: RAG 검색
    Milvus-->>AIServer: 관련 문서 청크들

    AIServer->>LLM: LLM 스트리밍 호출

    Note over AIServer,LLM: 실시간 토큰 스트리밍
    loop 토큰 스트림
        LLM-->>AIServer: 토큰 청크
        AIServer-->>ChatService: NDJSON 스트림<br/>{type: "token", content: "..."}
        ChatService-->>User: SSE 이벤트<br/>event:token<br/>data:토큰
    end

    LLM-->>AIServer: 완료
    AIServer-->>ChatService: NDJSON 완료<br/>{type: "done", answer, sources}
    ChatService->>DB: 전체 응답 저장
    ChatService-->>User: SSE 완료<br/>event:done<br/>data:END
```

---

## 3. 메시지 재시도 플로우

```mermaid
sequenceDiagram
    participant User as 사용자 (Frontend)
    participant ChatService as Chat Service<br/>(Backend)
    participant DB as Database
    participant AIServer as AI Server<br/>(Gateway)

    Note over User,AIServer: 실패한 메시지 재시도
    User->>ChatService: POST /chat/sessions/{sessionId}/messages/{messageId}/retry

    ChatService->>DB: 이전 메시지 조회<br/>(user 메시지 포함)
    DB-->>ChatService: 메시지 히스토리

    ChatService->>AIServer: POST /ai/chat/messages<br/>(동일한 요청 재전송)
    AIServer-->>ChatService: 새로운 응답

    ChatService->>DB: 기존 assistant 메시지 업데이트<br/>또는 새 메시지 생성
    DB-->>ChatService: 업데이트 완료

    ChatService-->>User: 재시도된 응답<br/>{id, content, tokensIn, tokensOut}
```

---

## 4. 피드백 제출 플로우

```mermaid
sequenceDiagram
    participant User as 사용자 (Frontend)
    participant ChatService as Chat Service<br/>(Backend)
    participant DB as Database

    Note over User,DB: 메시지 피드백
    User->>ChatService: POST /chat/sessions/{sessionId}/messages/{messageId}/feedback<br/>{score, comment}
    ChatService->>DB: 피드백 저장
    DB-->>ChatService: 저장 완료
    ChatService-->>User: 200 OK

    Note over User,DB: 세션 피드백
    User->>ChatService: POST /chat/sessions/{sessionId}/feedback<br/>{score, comment}
    ChatService->>DB: 세션 피드백 저장
    DB-->>ChatService: 저장 완료
    ChatService-->>User: 200 OK
```

---

## 5. 전체 채팅 생명주기 플로우

```mermaid
sequenceDiagram
    participant User as 사용자
    participant ChatService as Chat Service
    participant DB as Database
    participant AIServer as AI Server
    participant Milvus as Milvus
    participant LLM as LLM

    rect rgb(240, 248, 255)
        Note over User,DB: 세션 생성
        User->>ChatService: POST /api/chat/sessions
        ChatService->>DB: 세션 생성
        ChatService-->>User: sessionId
    end

    rect rgb(255, 250, 240)
        Note over User,LLM: 대화 진행 (여러 번 반복)
        loop 대화 루프
            User->>ChatService: POST /chat/messages
            ChatService->>DB: 사용자 메시지 저장
            ChatService->>AIServer: POST /ai/chat/messages
            AIServer->>Milvus: RAG 검색
            AIServer->>LLM: LLM 호출
            LLM-->>AIServer: 응답
            AIServer-->>ChatService: 응답 + sources
            ChatService->>DB: AI 메시지 저장
            ChatService-->>User: 응답
        end
    end

    rect rgb(240, 255, 240)
        Note over User,DB: 히스토리 조회
        User->>ChatService: GET /api/chat/sessions/{sessionId}/history
        ChatService->>DB: 메시지 조회
        ChatService-->>User: 전체 대화
    end

    rect rgb(255, 240, 240)
        Note over User,DB: 피드백 및 종료
        User->>ChatService: POST /chat/sessions/{sessionId}/feedback
        ChatService->>DB: 피드백 저장
        ChatService-->>User: 완료
    end
```

---

## 6. 에러 처리 플로우

```mermaid
sequenceDiagram
    participant User as 사용자
    participant ChatService as Chat Service
    participant AIServer as AI Server
    participant DB as Database

    User->>ChatService: POST /chat/messages
    ChatService->>DB: 사용자 메시지 저장

    ChatService->>AIServer: POST /ai/chat/messages

    alt AI 서버 오류
        AIServer-->>ChatService: 500 Internal Server Error<br/>{error_code: "LLM_ERROR"}
        ChatService->>DB: 메시지 상태를 FAILED로 저장
        ChatService-->>User: 500 에러 응답
    else 타임아웃
        AIServer-->>ChatService: 타임아웃
        ChatService->>DB: 메시지 상태를 TIMEOUT으로 저장
        ChatService-->>User: 503 Service Unavailable
    else 성공
        AIServer-->>ChatService: 정상 응답
        ChatService->>DB: 응답 저장
        ChatService-->>User: 정상 응답
    end
```

---

## 주요 엔드포인트 매핑

### Chat Service (Backend)

- `POST /api/chat/sessions` - 세션 생성
- `GET /api/chat/sessions/{sessionId}` - 세션 조회
- `GET /api/chat/sessions/{sessionId}/history` - 히스토리 조회
- `POST /chat/messages` - 메시지 전송
- `GET /chat/messages/{messageId}/stream` - SSE 스트리밍
- `POST /chat/sessions/{sessionId}/messages/{messageId}/retry` - 재시도
- `POST /chat/sessions/{sessionId}/messages/{messageId}/feedback` - 메시지 피드백
- `POST /chat/sessions/{sessionId}/feedback` - 세션 피드백

### AI Server (Gateway)

- `POST /ai/chat/messages` - 일반 채팅 응답
- `POST /ai/chat/stream` - 스트리밍 채팅 응답 (NDJSON)

**참고**: `/ai/search` 엔드포인트는 더 이상 사용되지 않습니다. (삭제됨)

---

## 데이터 흐름

1. **사용자 메시지**: Frontend → Chat Service → Database
2. **AI 요청**: Chat Service → AI Server
3. **RAG 검색**: AI Server → Milvus (Vector DB)
4. **LLM 호출**: AI Server → LLM Service
5. **AI 응답**: AI Server → Chat Service → Database → Frontend

---

**문서 버전**: 2025-12-19  
**작성자**: AI Assistant
