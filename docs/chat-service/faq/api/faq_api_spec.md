# FAQ Service API 명세서

## 개요

FAQ Service는 FAQ(자주 묻는 질문) 관리 및 조회 기능을 제공하는 서비스입니다.

- **Base URL**: `http://localhost:9005`
- **인증**: Bearer Token (JWT)

---

## 1. FAQ 관리 APIs (관리자)

**참고**: FAQ는 AI가 초안을 생성하고 관리자가 승인하는 구조입니다. 또한 관리자는 수동으로 FAQ를 생성할 수도 있습니다.

### 1.1 FAQ 수동 생성

관리자가 수동으로 FAQ를 생성합니다. 생성된 FAQ는 즉시 활성화되어 사용자에게 노출됩니다.

**Endpoint**: `POST /chat/faq`

**Request Body**:
```json
{
  "question": "비밀번호를 잊어버렸어요",
  "answer": "비밀번호 재설정 페이지에서 이메일을 입력하시면 재설정 링크를 보내드립니다.",
  "domain": "SECURITY",
  "priority": 1
}
```

**Response** (200 OK):
```json
"239d0429-b517-4897-beb0-bd1f699999da"
```

**필드 설명**:
- `question` (String, 필수): 질문 내용 (최대 500자)
- `answer` (String, 필수): 답변 내용 (최대 5000자)
- `domain` (String, 필수): 도메인 (예: SECURITY, POLICY, EDUCATION 등, 최대 50자)
- `priority` (Integer, 필수): 우선순위 (1~5, 값이 작을수록 상위 노출)

**참고**:
- 생성된 FAQ는 `isActive = true`, `publishedAt = 현재 시각`으로 설정되어 즉시 사용자에게 노출됩니다.
- 초기 데이터는 Flyway 마이그레이션(`V15__insert_initial_faq_data.sql`)을 통해 자동으로 삽입됩니다.

---

### 1.2 FAQ 수정

기존 FAQ를 수정합니다.

**Endpoint**: `PATCH /chat/faq/{faqId}`

**Path Parameters**:
- `faqId` (UUID): 수정할 FAQ ID

**Request Body**:
```json
{
  "question": "비밀번호를 잊어버렸어요 (수정)",
  "answer": "비밀번호 재설정 페이지에서 이메일을 입력하시면 재설정 링크를 보내드립니다. (수정)",
  "domain": "SECURITY",
  "isActive": true,
  "priority": 2
}
```

**Response** (200 OK): No Content

**필드 설명**:
- 모든 필드는 선택적입니다. null인 필드는 수정하지 않습니다.
- `isActive`: 활성 여부 (true: 노출, false: 비노출)

---

### 1.3 FAQ 삭제 (Soft Delete)

FAQ를 삭제합니다. 실제로는 `isActive = false`로 설정하는 소프트 삭제입니다.

**Endpoint**: `DELETE /chat/faq/{faqId}`

**Path Parameters**:
- `faqId` (UUID): 삭제할 FAQ ID

**Response** (200 OK): No Content

---

### 1.4 FAQ 목록 조회 (활성화된 FAQ만)

활성화된 FAQ 목록을 조회합니다.

**Endpoint**: `GET /chat/faq`

**Response** (200 OK):
```json
[
  {
    "id": "239d0429-b517-4897-beb0-bd1f699999da",
    "question": "비밀번호를 잊어버렸어요",
    "answer": "비밀번호 재설정 페이지에서 이메일을 입력하시면 재설정 링크를 보내드립니다.",
    "domain": "SECURITY",
    "isActive": true,
    "priority": 1,
    "createdAt": "2025-12-19T23:00:00Z",
    "updatedAt": "2025-12-19T23:00:00Z"
  }
]
```

---

## 2. FAQ 조회 APIs (사용자)

### 2.1 FAQ 홈 조회

홈 화면용 FAQ를 조회합니다. 도메인별로 1개씩 반환됩니다.

**Endpoint**: `GET /faq/home` 또는 `GET /faq/dashboard/home`

**Response** (200 OK):
```json
[
  {
    "id": "239d0429-b517-4897-beb0-bd1f699999da",
    "domain": "SECURITY",
    "question": "비밀번호를 잊어버렸어요",
    "answer": "비밀번호 재설정 페이지에서 이메일을 입력하시면 재설정 링크를 보내드립니다.",
    "publishedAt": "2025-12-19T23:00:00Z"
  },
  {
    "id": "350d0429-b517-4897-beb0-bd1f699999db",
    "domain": "POLICY",
    "question": "휴가 신청은 어떻게 하나요?",
    "answer": "인사 시스템에서 휴가 신청 메뉴를 통해 신청하실 수 있습니다.",
    "publishedAt": "2025-12-19T23:00:00Z"
  }
]
```

---

### 2.2 도메인별 FAQ 조회 (Query Parameter)

특정 도메인의 FAQ TOP 10을 조회합니다.

**Endpoint**: `GET /faq?domain={domain}`

**Query Parameters**:
- `domain` (String, 필수): 도메인 (예: SECURITY, POLICY, EDUCATION 등)

**Response** (200 OK):
```json
[
  {
    "id": "239d0429-b517-4897-beb0-bd1f699999da",
    "domain": "SECURITY",
    "question": "비밀번호를 잊어버렸어요",
    "answer": "비밀번호 재설정 페이지에서 이메일을 입력하시면 재설정 링크를 보내드립니다.",
    "publishedAt": "2025-12-19T23:00:00Z"
  }
]
```

---

### 2.3 도메인별 FAQ 조회 (Path Variable)

특정 도메인의 FAQ TOP 10을 조회합니다. (대시보드용)

**Endpoint**: `GET /faq/dashboard/{domain}`

**Path Parameters**:
- `domain` (String): 도메인 (예: SECURITY, POLICY, EDUCATION 등)

**Response** (200 OK):
```json
[
  {
    "id": "239d0429-b517-4897-beb0-bd1f699999da",
    "domain": "SECURITY",
    "question": "비밀번호를 잊어버렸어요",
    "answer": "비밀번호 재설정 페이지에서 이메일을 입력하시면 재설정 링크를 보내드립니다.",
    "publishedAt": "2025-12-19T23:00:00Z"
  }
]
```

---

## 3. FAQ 후보 관리 APIs (관리자)

### 3.1 FAQ 후보 생성

FAQ 후보를 생성합니다. FAQ 후보는 사용자가 자주 묻는 질문을 수집/집계하기 위한 엔티티입니다.

**Endpoint**: `POST /admin/faq/candidates`

**Request Body**:
```json
{
  "question": "비밀번호를 잊어버렸어요",
  "domain": "SECURITY"
}
```

**Response** (200 OK):
```json
"239d0429-b517-4897-beb0-bd1f699999da"
```

**필드 설명**:
- `question`: 후보 질문 (최대 500자, 필수)
- `domain`: 도메인 (최대 50자, 필수)

---

### 3.2 FAQ 후보 목록 조회

FAQ 후보 목록을 조회합니다.

**Endpoint**: `GET /admin/faq/candidates`

**Query Parameters**:
- `domain` (String, 선택): 도메인 필터
- `status` (String, 선택): 상태 필터 (NEW, ELIGIBLE, EXCLUDED)

**Response** (200 OK):
```json
[
  {
    "id": "239d0429-b517-4897-beb0-bd1f699999da",
    "canonicalQuestion": "비밀번호를 잊어버렸어요",
    "domain": "SECURITY",
    "questionCount7d": 15,
    "questionCount30d": 45,
    "avgIntentConfidence": 0.95,
    "piiDetected": false,
    "scoreCandidate": 8.5,
    "status": "ELIGIBLE",
    "lastAskedAt": "2025-12-19T23:00:00Z",
    "createdAt": "2025-12-19T20:00:00Z"
  }
]
```

**상태 설명**:
- `NEW`: 새로 생성된 후보
- `ELIGIBLE`: FAQ 초안 생성 가능한 후보
- `EXCLUDED`: 제외된 후보

---

### 3.3 FAQ 후보에서 Draft 생성

FAQ 후보를 기반으로 AI를 사용하여 FAQ 초안을 생성합니다.

**Endpoint**: `POST /admin/faq/candidates/{candidateId}/generate`

**Path Parameters**:
- `candidateId` (UUID): FAQ 후보 ID

**Response** (200 OK):
```json
{
  "draftId": "350d0429-b517-4897-beb0-bd1f699999db"
}
```

**참고**:
- AI 서버가 RAGFlow를 직접 호출하여 FAQ 초안을 생성합니다.
- RAGFlow 서버가 실행 중이 아니면 Draft 생성이 실패할 수 있습니다.
- PII가 감지되었거나 의도 신뢰도가 0.7 미만인 후보는 Draft 생성이 불가능합니다.

---

### 3.4 FAQ 초안 배치 생성

여러 FAQ 후보를 기반으로 FAQ 초안을 배치로 생성합니다.

**Endpoint**: `POST /admin/faq/candidates/generate/batch`

**Request Body**:
```json
{
  "items": [
    {
      "domain": "SEC_POLICY",
      "clusterId": "cluster-123",
      "canonicalQuestion": "비밀번호를 잊어버렸어요",
      "sampleQuestions": ["비밀번호 분실", "비밀번호 재설정 방법"],
      "topDocs": [
        {
          "docId": "doc-123",
          "docVersion": "v1",
          "title": "비밀번호 관리 정책",
          "snippet": "비밀번호 재설정 방법...",
          "articleLabel": "보안",
          "articlePath": "/security/password",
          "score": 0.95,
          "page": 1,
          "dataset": "security",
          "source": "internal"
        }
      ],
      "avgIntentConfidence": 0.95
    }
  ],
  "concurrency": 5
}
```

**Response** (200 OK):
```json
{
  "items": [
    {
      "draftId": "350d0429-b517-4897-beb0-bd1f699999db",
      "status": "SUCCESS",
      "question": "비밀번호를 잊어버렸어요",
      "answer": "비밀번호 재설정 페이지에서...",
      "errorMessage": null
    }
  ],
  "totalCount": 1,
  "successCount": 1,
  "failedCount": 0
}
```

**참고**:
- 여러 FAQ 초안을 동시에 생성할 수 있습니다.
- `concurrency`는 동시 처리 수를 제한합니다 (선택적).

---

### 3.5 자동 FAQ 생성

사용자 질문 로그를 분석하여 자동으로 FAQ 후보를 선정하고 초안을 생성합니다.

**Endpoint**: `POST /admin/faq/candidates/auto-generate`

**Request Body**:
```json
{
  "domain": "SEC_POLICY",
  "minFrequency": 3,
  "daysBack": 30,
  "maxCandidates": 20,
  "autoGenerateDrafts": true
}
```

**Response** (200 OK):
```json
{
  "status": "SUCCESS",
  "candidatesFound": 15,
  "draftsGenerated": 12,
  "draftsFailed": 3,
  "drafts": [
    {
      "draftId": "350d0429-b517-4897-beb0-bd1f699999db",
      "status": "SUCCESS",
      "question": "비밀번호를 잊어버렸어요",
      "answer": "비밀번호 재설정 페이지에서...",
      "errorMessage": null
    }
  ],
  "errorMessage": null
}
```

**필드 설명**:
- `domain` (String, 선택): 도메인 필터 (예: SEC_POLICY, PII_PRIVACY)
- `minFrequency` (Integer, 선택): 최소 질문 빈도 (기본 3회)
- `daysBack` (Integer, 선택): 조회 기간 일수 (기본 30일)
- `maxCandidates` (Integer, 선택): 최대 후보 수 (기본 20개)
- `autoGenerateDrafts` (Boolean, 선택): 자동으로 FAQ 초안 생성 여부 (기본 true)

**참고**:
- 사용자 질문 로그를 분석하여 자주 묻는 질문을 자동으로 식별합니다.
- `autoGenerateDrafts`가 true이면 후보 선정 후 자동으로 초안을 생성합니다.

---

## 4. FAQ Draft 관리 APIs (관리자)

### 4.1 FAQ Draft 목록 조회

FAQ 초안 목록을 조회합니다.

**Endpoint**: `GET /admin/faq/drafts`

**Query Parameters**:
- `domain` (String, 선택): 도메인 필터
- `status` (String, 선택): 상태 필터 (DRAFT, PUBLISHED, REJECTED 또는 PENDING)

**Response** (200 OK):
```json
[
  {
    "id": "350d0429-b517-4897-beb0-bd1f699999db",
    "domain": "SECURITY",
    "question": "비밀번호를 잊어버렸어요",
    "summary": "비밀번호 재설정 방법에 대한 FAQ 초안",
    "status": "DRAFT",
    "createdAt": "2025-12-19T22:00:00"
  }
]
```

**상태 설명**:
- `DRAFT`: 초안 상태
- `PUBLISHED`: 게시됨 (승인 후 FAQ로 생성됨)
- `REJECTED`: 반려됨
- `PENDING`: 요청 시 `DRAFT`로 자동 매핑됩니다.

---

### 4.2 FAQ Draft 승인

FAQ 초안을 승인하여 새로운 FAQ를 생성합니다.

**Endpoint**: `POST /admin/faq/drafts/{draftId}/approve`

**Path Parameters**:
- `draftId` (UUID): FAQ 초안 ID

**Query Parameters**:
- `reviewerId` (UUID, 필수): 승인자 ID
- `question` (String, 필수): 승인할 질문 내용 (URL 인코딩 필요)
- `answer` (String, 필수): 승인할 답변 내용 (URL 인코딩 필요)

**Response** (200 OK): No Content

**참고**:
- Draft를 승인하면 새로운 FAQ가 생성되고 Draft 상태가 `PUBLISHED`로 변경됩니다.
- Query Parameter의 `question`과 `answer`는 URL 인코딩이 필요합니다.

---

### 4.3 FAQ Draft 반려

FAQ 초안을 반려합니다.

**Endpoint**: `POST /admin/faq/drafts/{draftId}/reject`

**Path Parameters**:
- `draftId` (UUID): FAQ 초안 ID

**Query Parameters**:
- `reviewerId` (UUID, 필수): 반려자 ID
- `reason` (String, 필수): 반려 사유 (URL 인코딩 필요)

**Response** (200 OK): No Content

**참고**:
- Draft 상태가 `REJECTED`로 변경됩니다.
- Query Parameter의 `reason`은 URL 인코딩이 필요합니다.

---

## 5. FAQ 통합 관리 APIs (관리자)

### 5.1 FAQ 후보에서 Draft 생성 (통합)

FAQ 후보를 기반으로 AI를 사용하여 FAQ 초안을 생성합니다. (AdminFaqController)

**Endpoint**: `POST /admin/faqs/candidates/{candidateId}/generate`

**Path Parameters**:
- `candidateId` (UUID): FAQ 후보 ID

**Response** (200 OK):
```json
"350d0429-b517-4897-beb0-bd1f699999db"
```

**참고**:
- `AdminFaqCandidateController`의 `/admin/faq/candidates/{candidateId}/generate`와 동일한 기능입니다.

---

### 5.2 FAQ Draft 승인 (통합)

FAQ 초안을 승인하여 새로운 FAQ를 생성합니다. (AdminFaqController)

**Endpoint**: `POST /admin/faqs/drafts/{draftId}/approve`

**Path Parameters**:
- `draftId` (UUID): FAQ 초안 ID

**Query Parameters**:
- `reviewerId` (UUID, 필수): 승인자 ID
- `question` (String, 필수): 승인할 질문 내용 (URL 인코딩 필요)
- `answer` (String, 필수): 승인할 답변 내용 (URL 인코딩 필요)

**Response** (200 OK): No Content

**참고**:
- `AdminFaqDraftController`의 `/admin/faq/drafts/{draftId}/approve`와 동일한 기능입니다.
- Query Parameter의 `question`과 `answer`는 URL 인코딩이 필요합니다.

---

### 5.3 FAQ Draft 반려 (통합)

FAQ 초안을 반려합니다. (AdminFaqController)

**Endpoint**: `POST /admin/faqs/drafts/{draftId}/reject`

**Path Parameters**:
- `draftId` (UUID): FAQ 초안 ID

**Query Parameters**:
- `reviewerId` (UUID, 필수): 반려자 ID
- `reason` (String, 필수): 반려 사유 (URL 인코딩 필요)

**Response** (200 OK): No Content

**참고**:
- `AdminFaqDraftController`의 `/admin/faq/drafts/{draftId}/reject`와 동일한 기능입니다.
- Query Parameter의 `reason`은 URL 인코딩이 필요합니다.

---

## 6. FAQ UI 카테고리 관리 APIs (관리자)

### 6.1 UI 카테고리 생성

FAQ UI 카테고리를 생성합니다.

**Endpoint**: `POST /admin/faq/ui-categories?operatorId={operatorId}`

**Query Parameters**:
- `operatorId` (UUID, 필수): 운영자 ID

**Request Body**:
```json
{
  "slug": "security",
  "displayName": "보안",
  "sortOrder": 1
}
```

**Response** (200 OK):
```json
"239d0429-b517-4897-beb0-bd1f699999da"
```

**필드 설명**:
- `slug`: URL 슬러그 (예: "security")
- `displayName`: 표시 이름 (예: "보안")
- `sortOrder`: 정렬 순서 (작을수록 상위)

---

### 6.2 UI 카테고리 수정

FAQ UI 카테고리를 수정합니다.

**Endpoint**: `PATCH /admin/faq/ui-categories/{categoryId}?operatorId={operatorId}`

**Path Parameters**:
- `categoryId` (UUID): 카테고리 ID

**Query Parameters**:
- `operatorId` (UUID, 필수): 운영자 ID

**Request Body**:
```json
{
  "slug": "security-updated",
  "displayName": "보안 (수정)",
  "sortOrder": 2
}
```

**Response** (200 OK): No Content

---

### 6.3 UI 카테고리 비활성화

FAQ UI 카테고리를 비활성화합니다.

**Endpoint**: `POST /admin/faq/ui-categories/{categoryId}/deactivate?operatorId={operatorId}&reason={reason}`

**Path Parameters**:
- `categoryId` (UUID): 카테고리 ID

**Query Parameters**:
- `operatorId` (UUID, 필수): 운영자 ID
- `reason` (String, 선택): 비활성화 사유

**Response** (200 OK): No Content

---

### 6.4 UI 카테고리 목록 조회

모든 UI 카테고리 목록을 조회합니다.

**Endpoint**: `GET /admin/faq/ui-categories`

**Response** (200 OK):
```json
[
  {
    "id": "239d0429-b517-4897-beb0-bd1f699999da",
    "slug": "security",
    "displayName": "보안",
    "sortOrder": 1,
    "isActive": true,
    "createdAt": "2025-12-19T23:00:00Z",
    "updatedAt": "2025-12-19T23:00:00Z"
  }
]
```

---

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
- `422 Unprocessable Entity`: 요청은 이해했지만 처리할 수 없음
- `500 Internal Server Error`: 서버 오류

---

## 참고사항

- 모든 UUID는 표준 UUID 형식입니다.
- 모든 날짜/시간은 ISO 8601 형식 (UTC)입니다.
- 인증이 필요한 API는 `Authorization: Bearer {token}` 헤더를 포함해야 합니다.
- **초기 데이터**: 서비스 시작 시 Flyway 마이그레이션(`V15__insert_initial_faq_data.sql`)을 통해 기본 FAQ 9개가 자동으로 삽입됩니다 (SECURITY 3개, POLICY 3개, EDUCATION 3개).
- **FAQ 생성 방식**:
  - **자동 생성**: 사용자 질문 로그를 분석하여 AI가 후보를 선정하고 초안을 생성한 후, 관리자가 승인하여 FAQ로 생성
  - **수동 생성**: 관리자가 직접 FAQ를 생성 (즉시 활성화되어 사용자에게 노출)
- FAQ 후보는 사용자 질문을 분석하여 자동으로 생성될 수 있습니다.
- FAQ Draft는 AI 서비스를 통해 자동 생성되며, 관리자가 검토 후 승인/반려할 수 있습니다.
- FAQ는 승인된 Draft를 기반으로 생성되거나, 관리자가 수동으로 생성할 수 있습니다.
- Draft 생성 시 AI 서버가 RAGFlow를 직접 호출합니다. RAGFlow 서버가 실행 중이 아니면 Draft 생성이 실패할 수 있습니다.
- Draft 목록 조회 시 `status=PENDING`을 사용하면 자동으로 `DRAFT`로 매핑됩니다.
- Query Parameter에 한글이 포함된 경우 URL 인코딩이 필요합니다 (예: `question`, `answer`, `reason`).
