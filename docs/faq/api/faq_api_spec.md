# FAQ API 명세서

## 개요

FAQ(Frequently Asked Questions) 서비스는 자주 묻는 질문과 답변을 관리하는 서비스입니다.

- **Base URL**: `http://localhost:9001`
- **인증**: Bearer Token (JWT) - 관리자 API는 인증 필요

---

## 1. FAQ CRUD APIs

### 1.1 FAQ 생성

새로운 FAQ를 생성합니다. (관리자 전용)

**Endpoint**: `POST /chat/faq`

**Request Body**:
```json
{
  "question": "비밀번호를 잊어버렸어요",
  "answer": "비밀번호 재설정 페이지에서 이메일 인증을 통해 재설정할 수 있습니다.",
  "domain": "SECURITY",
  "priority": 1
}
```

**필드 설명**:
- `question` (String, required): 질문 (최대 500자)
- `answer` (String, required): 답변 (최대 5000자)
- `domain` (String, required): 도메인 (예: SECURITY, POLICY, EDUCATION 등, 최대 50자)
- `priority` (Integer, required): 우선순위 (1~5, 값이 작을수록 상위 노출)

**Response** (200 OK):
```json
"a943833d-f980-4d08-9fa9-d44078330689"
```

---

### 1.2 FAQ 수정

기존 FAQ를 수정합니다. (관리자 전용)

**Endpoint**: `PATCH /chat/faq/{id}`

**Path Parameters**:
- `id` (UUID): 수정할 FAQ ID

**Request Body**:
```json
{
  "question": "수정된 질문",
  "answer": "수정된 답변",
  "domain": "POLICY",
  "isActive": true,
  "priority": 2
}
```

**필드 설명** (모두 optional):
- `question` (String): 질문 (최대 500자)
- `answer` (String): 답변 (최대 5000자)
- `domain` (String): 도메인 (최대 50자)
- `isActive` (Boolean): 활성 여부 (true: 노출, false: 비노출)
- `priority` (Integer): 우선순위 (1~5)

**Response** (200 OK): No Content

---

### 1.3 FAQ 삭제 (Soft Delete)

FAQ를 삭제합니다. 실제로는 `isActive`를 false로 설정하는 소프트 삭제입니다. (관리자 전용)

**Endpoint**: `DELETE /chat/faq/{id}`

**Path Parameters**:
- `id` (UUID): 삭제할 FAQ ID

**Response** (200 OK): No Content

---

### 1.4 FAQ 조회

활성화된 모든 FAQ 목록을 조회합니다. (사용자용)

**Endpoint**: `GET /chat/faq`

**Response** (200 OK):
```json
[
  {
    "id": "a943833d-f980-4d08-9fa9-d44078330689",
    "question": "비밀번호를 잊어버렸어요",
    "answer": "비밀번호 재설정 페이지에서 이메일 인증을 통해 재설정할 수 있습니다.",
    "domain": "SECURITY",
    "isActive": true,
    "priority": 1,
    "createdAt": "2025-12-19T23:00:00Z",
    "updatedAt": "2025-12-19T23:00:00Z"
  }
]
```

---

## 2. FAQ Query APIs

### 2.1 FAQ 홈 조회

홈 화면용 FAQ를 조회합니다. 도메인별 1개씩 반환됩니다.

**Endpoint**: `GET /faq/home`

**Response** (200 OK):
```json
[
  {
    "id": "a943833d-f980-4d08-9fa9-d44078330689",
    "domain": "SECURITY",
    "question": "비밀번호를 잊어버렸어요",
    "answer": "비밀번호 재설정 페이지에서 이메일 인증을 통해 재설정할 수 있습니다.",
    "publishedAt": "2025-12-19T23:00:00Z"
  }
]
```

---

### 2.2 도메인별 FAQ 조회

특정 도메인의 FAQ TOP 10을 조회합니다.

**Endpoint**: `GET /faq`

**Query Parameters**:
- `domain` (String, required): 도메인 (예: SECURITY, POLICY, EDUCATION 등)

**Response** (200 OK):
```json
[
  {
    "id": "a943833d-f980-4d08-9fa9-d44078330689",
    "domain": "SECURITY",
    "question": "비밀번호를 잊어버렸어요",
    "answer": "비밀번호 재설정 페이지에서 이메일 인증을 통해 재설정할 수 있습니다.",
    "publishedAt": "2025-12-19T23:00:00Z"
  }
]
```

---

## 3. FAQ Dashboard APIs

### 3.1 대시보드 홈 FAQ

대시보드 홈 화면용 FAQ를 조회합니다. 도메인별 1개씩 반환됩니다.

**Endpoint**: `GET /faq/dashboard/home`

**Response** (200 OK):
```json
[
  {
    "id": "a943833d-f980-4d08-9fa9-d44078330689",
    "domain": "SECURITY",
    "question": "비밀번호를 잊어버렸어요",
    "answer": "비밀번호 재설정 페이지에서 이메일 인증을 통해 재설정할 수 있습니다.",
    "publishedAt": "2025-12-19T23:00:00Z"
  }
]
```

---

### 3.2 대시보드 도메인별 FAQ

특정 도메인의 TOP 10 FAQ를 조회합니다.

**Endpoint**: `GET /faq/dashboard/{domain}`

**Path Parameters**:
- `domain` (String): 도메인 (예: SECURITY, POLICY, EDUCATION 등)

**Response** (200 OK):
```json
[
  {
    "id": "a943833d-f980-4d08-9fa9-d44078330689",
    "domain": "SECURITY",
    "question": "비밀번호를 잊어버렸어요",
    "answer": "비밀번호 재설정 페이지에서 이메일 인증을 통해 재설정할 수 있습니다.",
    "publishedAt": "2025-12-19T23:00:00Z"
  }
]
```

---

## 4. Admin FAQ Candidate APIs

### 4.1 FAQ 후보 목록 조회

FAQ 후보 목록을 조회합니다. (관리자 전용)

**Endpoint**: `GET /admin/faq/candidates`

**Query Parameters**:
- `domain` (String, optional): 도메인 필터
- `status` (String, optional): 상태 필터 (NEW, ELIGIBLE, EXCLUDED)

**Response** (200 OK):
```json
[
  {
    "id": "candidate-uuid",
    "canonicalQuestion": "대표 질문",
    "domain": "SECURITY",
    "questionCount7d": 10,
    "questionCount30d": 45,
    "avgIntentConfidence": 0.95,
    "piiDetected": false,
    "scoreCandidate": 8.5,
    "status": "ELIGIBLE",
    "lastAskedAt": "2025-12-19T23:00:00Z",
    "createdAt": "2025-12-19T23:00:00Z"
  }
]
```

---

### 4.2 FAQ 후보에서 초안 생성

FAQ 후보를 기반으로 AI 초안을 생성합니다. (관리자 전용)

**Endpoint**: `POST /admin/faq/candidates/{candidateId}/generate`

**Path Parameters**:
- `candidateId` (UUID): 후보 ID

**Response** (200 OK):
```json
{
  "draftId": "draft-uuid"
}
```

---

## 5. Admin FAQ Draft APIs

### 5.1 FAQ 초안 목록 조회

FAQ 초안 목록을 조회합니다. (관리자 전용)

**Endpoint**: `GET /admin/faq/drafts`

**Query Parameters**:
- `domain` (String, optional): 도메인 필터
- `status` (String, optional): 상태 필터 (DRAFT, APPROVED, REJECTED 등)

**Response** (200 OK):
```json
[
  {
    "id": "draft-uuid",
    "domain": "SECURITY",
    "question": "AI가 생성한 질문",
    "summary": "요약",
    "status": "DRAFT",
    "createdAt": "2025-12-19T23:00:00"
  }
]
```

---

### 5.2 FAQ 초안 승인

FAQ 초안을 승인하여 게시 FAQ로 생성합니다. (관리자 전용)

**Endpoint**: `POST /admin/faq/drafts/{draftId}/approve`

**Path Parameters**:
- `draftId` (UUID): 초안 ID

**Query Parameters**:
- `reviewerId` (UUID, required): 승인자 ID
- `question` (String, required): 최종 질문
- `answer` (String, required): 최종 답변

**Response** (200 OK): No Content

---

### 5.3 FAQ 초안 반려

FAQ 초안을 반려합니다. (관리자 전용)

**Endpoint**: `POST /admin/faq/drafts/{draftId}/reject`

**Path Parameters**:
- `draftId` (UUID): 초안 ID

**Query Parameters**:
- `reviewerId` (UUID, required): 반려자 ID
- `reason` (String, required): 반려 사유

**Response** (200 OK): No Content

---

## 6. Admin FAQ Controller APIs (별도 경로)

### 6.1 FAQ 후보에서 초안 생성 (별도 경로)

**Endpoint**: `POST /admin/faqs/candidates/{id}/generate`

**Path Parameters**:
- `id` (UUID): 후보 ID

**Response** (200 OK):
```json
"draft-uuid"
```

---

### 6.2 FAQ 초안 승인 (별도 경로)

**Endpoint**: `POST /admin/faqs/drafts/{id}/approve`

**Path Parameters**:
- `id` (UUID): 초안 ID

**Query Parameters**:
- `reviewerId` (UUID, required): 승인자 ID
- `question` (String, required): 최종 질문
- `answer` (String, required): 최종 답변

**Response** (200 OK): No Content

---

### 6.3 FAQ 초안 반려 (별도 경로)

**Endpoint**: `POST /admin/faqs/drafts/{id}/reject`

**Path Parameters**:
- `id` (UUID): 초안 ID

**Query Parameters**:
- `reviewerId` (UUID, required): 반려자 ID
- `reason` (String, required): 반려 사유

**Response** (200 OK): No Content

---

## 7. Admin FAQ UI Category APIs

### 7.1 UI 카테고리 생성

FAQ UI 카테고리를 생성합니다. (관리자 전용)

**Endpoint**: `POST /admin/faq/ui-categories`

**Query Parameters**:
- `operatorId` (UUID, required): 운영자 ID

**Request Body**:
```json
{
  "slug": "security-faq",
  "displayName": "보안 FAQ",
  "sortOrder": 1
}
```

**Response** (200 OK):
```json
"category-uuid"
```

---

### 7.2 UI 카테고리 수정

FAQ UI 카테고리를 수정합니다. (관리자 전용)

**Endpoint**: `PATCH /admin/faq/ui-categories/{categoryId}`

**Path Parameters**:
- `categoryId` (UUID): 카테고리 ID

**Query Parameters**:
- `operatorId` (UUID, required): 운영자 ID

**Request Body**:
```json
{
  "displayName": "수정된 카테고리명",
  "sortOrder": 2,
  "isActive": true
}
```

**Response** (200 OK): No Content

---

### 7.3 UI 카테고리 비활성화

FAQ UI 카테고리를 비활성화합니다. (관리자 전용)

**Endpoint**: `POST /admin/faq/ui-categories/{categoryId}/deactivate`

**Path Parameters**:
- `categoryId` (UUID): 카테고리 ID

**Query Parameters**:
- `operatorId` (UUID, required): 운영자 ID
- `reason` (String, optional): 비활성화 사유

**Response** (200 OK): No Content

---

### 7.4 UI 카테고리 조회

전체 UI 카테고리 목록을 조회합니다. (관리자 전용)

**Endpoint**: `GET /admin/faq/ui-categories`

**Response** (200 OK):
```json
[
  {
    "id": "category-uuid",
    "slug": "security-faq",
    "displayName": "보안 FAQ",
    "sortOrder": 1,
    "isActive": true,
    "createdAt": "2025-12-19T23:00:00Z",
    "updatedAt": "2025-12-19T23:00:00Z"
  }
]
```

---

## 8. Admin FAQ Seed API

### 8.1 FAQ 초기 데이터 CSV 업로드

FAQ 초기 데이터를 CSV 파일로 업로드합니다. (관리자 전용)

**Endpoint**: `POST /admin/faq/seed/upload`

**Content-Type**: `multipart/form-data`

**Query Parameters**:
- `operatorId` (UUID, required): 운영자 ID

**Request Body**:
- `file` (MultipartFile, required): CSV 파일

**Response** (200 OK): No Content

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

### 주요 에러 메시지

- `FAQ 후보가 존재하지 않습니다.`: 존재하지 않는 후보 ID
- `FAQ 초안이 없습니다.`: 존재하지 않는 초안 ID
- `카테고리가 존재하지 않습니다.`: 존재하지 않는 카테고리 ID
- `이미 존재하는 slug 입니다.`: 중복된 slug

---

## 참고사항

- 모든 UUID는 표준 UUID 형식입니다.
- 모든 날짜/시간은 ISO 8601 형식 (UTC)입니다.
- 관리자 API는 `Authorization: Bearer {token}` 헤더를 포함해야 합니다.
- `priority` 값이 작을수록 상위 노출됩니다.
- FAQ는 소프트 삭제 방식으로, `isActive`가 false로 설정됩니다.

