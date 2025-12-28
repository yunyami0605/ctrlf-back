# 콘텐츠 검토 데스크 페이지 플로우

검토자가 콘텐츠를 승인/반려하고 감사 이력을 관리하는 페이지의 API 플로우입니다.

## 1. 페이지 진입 및 초기 데이터 조회

```
1. GET /admin/videos/review-stats
   → 검토 통계 조회
   → Response: {
       pendingCount: 63,      // 검토 대기
       approvedCount: 47,     // 승인됨
       rejectedCount: 17,     // 반려됨
       myActivityCount: 64     // 내 활동
     }

2. GET /admin/videos/review-queue?status=pending&page=0&size=30
   → 검토 대기 목록 조회 (기본값)
   → Response: {
       items: [{
         videoId: "영상 ID",
         educationId: "교육 ID",
         educationTitle: "교육 제목",
         videoTitle: "영상 제목",
         status: "SCRIPT_REVIEW_REQUESTED" | "FINAL_REVIEW_REQUESTED",
         reviewStage: "1차" | "2차",
         creatorDepartment: "ESG팀",
         creatorName: "서제작",
         creatorUuid: "제작자 UUID",
         submittedAt: "2025.12.27T17:31:00Z",
         category: "JOB_DUTY",
         eduType: "MANDATORY"
       }],
       totalCount: 63,
       page: 0,
       size: 30,
       totalPages: 3,
       firstRoundCount: 8,    // 1차 검토 대기
       secondRoundCount: 19,  // 2차 검토 대기
       documentCount: 36
     }
```

## 2. 상태 필터 변경

```
GET /admin/videos/review-queue?status={status}&page=0&size=30

status 값:
- pending (기본값): 검토 대기 (SCRIPT_REVIEW_REQUESTED, FINAL_REVIEW_REQUESTED)
- approved: 승인됨 (PUBLISHED)
- rejected: 반려됨 (EducationVideoReview가 있는 영상)
```

**예시:**

- 검토 대기: `GET /admin/videos/review-queue?status=pending`
- 승인됨: `GET /admin/videos/review-queue?status=approved`
- 반려됨: `GET /admin/videos/review-queue?status=rejected`

## 3. 검색 및 필터링

```
GET /admin/videos/review-queue?status=pending&search={검색어}&myProcessingOnly={true|false}&reviewStage={first|second|document|all}&sort={latest|oldest|title}&page=0&size=30

파라미터:
- search: 제목/부서/제작자 검색 (예: "성희롱", "ESG팀", "서제작")
- myProcessingOnly: 내 처리만 필터 (true/false)
- reviewStage: 검토 단계 필터 (first: 1차, second: 2차, document: 문서, all: 전체)
- sort: 정렬 옵션 (latest: 최신순, oldest: 오래된순, title: 제목순, 기본값: latest)
- page: 페이지 번호 (0-base)
- size: 페이지 크기 (기본값: 30)
```

**검색 범위:**

- 영상 제목
- 교육 제목
- 제작자 이름
- 제작자 부서

**검토 단계 필터:**

- `first`: 1차 검토만 (SCRIPT_REVIEW_REQUESTED 상태)
- `second`: 2차 검토만 (FINAL_REVIEW_REQUESTED 상태)
- `document`: 문서 타입 (현재는 모든 영상 포함)
- `all` 또는 생략: 전체

**정렬 옵션:**

- `latest`: 최신순 (기본값)
- `oldest`: 오래된순
- `title`: 제목순 (가나다순)

## 4. 영상 상세 조회 (우측 패널)

### 4.1 검토 상세 정보 조회

```
GET /admin/videos/{videoId}/review-detail
→ 검토 화면에 필요한 영상 상세 정보 조회
→ Response: {
    videoId: "영상 ID",
    educationId: "교육 ID",
    educationTitle: "교육 제목",
    videoTitle: "영상 제목",
    status: "SCRIPT_REVIEW_REQUESTED" | "FINAL_REVIEW_REQUESTED" | "PUBLISHED",
    reviewStage: "1차" | "2차" | "승인됨" | "1차 반려" | "2차 반려",
    creatorDepartment: "ESG팀",
    creatorName: "서제작",
    creatorUuid: "제작자 UUID",
    submittedAt: "2025.12.27T17:31:00Z",
    updatedAt: "2025.12.27T17:31:00Z",
    category: "JOB_DUTY",
    eduType: "MANDATORY",
    scriptId: "스크립트 ID",
    scriptVersion: 1
  }
```

### 4.2 감사 이력 조회

```
GET /admin/videos/{videoId}/review-history
→ 영상의 감사 이력 조회
→ Response: {
    videoId: "영상 ID",
    videoTitle: "영상 제목",
    history: [
      {
        eventType: "CREATED",
        description: "영상 생성",
        timestamp: "2025.12.17T00:31:00Z",
        actorName: "서제작",
        actorUuid: "제작자 UUID",
        rejectionReason: null,
        rejectionStage: null
      },
      {
        eventType: "AUTO_CHECKED",
        description: "PII/금칙어/품질 점검",
        timestamp: "2025.12.27T17:31:00Z",
        actorName: "SYSTEM",
        actorUuid: null,
        rejectionReason: null,
        rejectionStage: null
      },
      {
        eventType: "REJECTED",
        description: "검토 반려",
        timestamp: "2025.12.27T18:00:00Z",
        actorName: "리뷰어 이름",
        actorUuid: "리뷰어 UUID",
        rejectionReason: "반려 사유",
        rejectionStage: "SCRIPT" | "VIDEO"
      }
    ]
  }
```

## 5. 승인 처리

```
PUT /admin/videos/{videoId}/approve
→ 영상 승인
→ 1차 승인: SCRIPT_REVIEW_REQUESTED → SCRIPT_APPROVED (스크립트 승인, 영상 생성 가능)
→ 2차 승인: FINAL_REVIEW_REQUESTED → PUBLISHED (영상 승인 = 게시)
→ Response: {
    videoId: "영상 ID",
    previousStatus: "SCRIPT_REVIEW_REQUESTED" | "FINAL_REVIEW_REQUESTED",
    currentStatus: "SCRIPT_APPROVED" | "PUBLISHED",
    updatedAt: "2025.12.27T18:00:00Z"
  }
```

**참고:**

- 1차 승인 후 영상 생성이 가능해집니다.
- 2차 승인 시 자동으로 PUBLISHED 상태가 되어 교육이 노출됩니다.

## 6. 반려 처리

```
PUT /admin/videos/{videoId}/reject
→ 영상 반려
→ Request: {
    reason: "반려 사유" (선택)
  }
→ 1차 반려: SCRIPT_REVIEW_REQUESTED → SCRIPT_READY (스크립트 검토 단계 반려)
→ 2차 반려: FINAL_REVIEW_REQUESTED → READY (영상 검토 단계 반려)
→ Response: {
    videoId: "영상 ID",
    previousStatus: "SCRIPT_REVIEW_REQUESTED" | "FINAL_REVIEW_REQUESTED",
    currentStatus: "SCRIPT_READY" | "READY",
    updatedAt: "2025.12.27T18:00:00Z"
  }
```

**참고:**

- 반려 사유(reason)가 제공되면 `EducationVideoReview` 테이블에 저장됩니다.
- 반려 단계(SCRIPT/VIDEO)도 함께 저장됩니다.

## 7. 통계 갱신

승인/반려 후 통계를 다시 조회:

```
GET /admin/videos/review-stats
→ 업데이트된 통계 조회
```

## 주요 플로우

```
[검토 데스크 페이지 진입]
│
├─→ GET /admin/videos/review-stats (통계 조회)
│
├─→ GET /admin/videos/review-queue?status=pending (검토 대기 목록)
│
├─→ [상태 필터 변경]
│   ├─→ GET /admin/videos/review-queue?status=pending (검토 대기)
│   ├─→ GET /admin/videos/review-queue?status=approved (승인됨)
│   └─→ GET /admin/videos/review-queue?status=rejected (반려됨)
│
├─→ [검색/필터링]
│   └─→ GET /admin/videos/review-queue?status=pending&search={검색어}&myProcessingOnly={true|false}&reviewStage={first|second|document}&sort={latest|oldest|title}
│
├─→ [영상 선택]
│   ├─→ GET /admin/videos/{videoId}/review-detail (상세 정보)
│   └─→ GET /admin/videos/{videoId}/review-history (감사 이력)
│
└─→ [승인/반려 처리]
    ├─→ PUT /admin/videos/{videoId}/approve (승인)
    └─→ PUT /admin/videos/{videoId}/reject (반려)
        └─→ GET /admin/videos/review-stats (통계 갱신)
        └─→ GET /admin/videos/review-queue (목록 갱신)
```

## 플로우 다이어그램

```
┌─────────────────────────────────────────────────────────┐
│              검토 데스크 페이지 진입                      │
└──────────────────┬──────────────────────────────────────┘
                   │
        ┌──────────┴──────────┐
        │                     │
        ▼                     ▼
┌───────────────┐    ┌──────────────────┐
│ 통계 조회      │    │ 검토 목록 조회     │
│ review-stats  │    │ review-queue     │
└───────────────┘    └────────┬─────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        │                     │                     │
        ▼                     ▼                     ▼
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│ 상태 필터     │    │ 검색/필터링   │    │ 영상 선택     │
│ pending      │    │ search       │    │ review-detail│
│ approved     │    │ myProcessing │    │ review-history│
│ rejected     │    │ Only         │    │              │
└──────────────┘    └──────────────┘    └──────┬───────┘
                                                │
                                    ┌───────────┴───────────┐
                                    │                      │
                                    ▼                      ▼
                            ┌──────────────┐    ┌──────────────┐
                            │ 승인 처리      │    │ 반려 처리      │
                            │ approve      │    │ reject       │
                            └──────┬───────┘    └──────┬───────┘
                                   │                   │
                                   └─────────┬─────────┘
                                             │
                                             ▼
                                    ┌──────────────┐
                                    │ 통계/목록 갱신 │
                                    └──────────────┘
```

## 참고사항

1. **상태 필터:**

   - `pending`: 검토 대기 상태 (`SCRIPT_REVIEW_REQUESTED`, `FINAL_REVIEW_REQUESTED`)
   - `approved`: 승인된 영상 (`PUBLISHED`)
   - `rejected`: 반려된 영상 (`EducationVideoReview`가 있는 영상)

2. **검색 기능:**

   - 제목, 교육 제목, 제작자 이름, 제작자 부서로 검색 가능
   - 제작자 정보는 infra-service에서 실시간 조회

3. **내 처리만 필터:**

   - `myProcessingOnly=true`일 때, 현재 검토자가 처리한 영상만 표시
   - `EducationVideoReview`에서 리뷰어 UUID로 필터링

4. **검토 단계 필터:**

   - `reviewStage=first`: 1차 검토만 (SCRIPT_REVIEW_REQUESTED)
   - `reviewStage=second`: 2차 검토만 (FINAL_REVIEW_REQUESTED)
   - `reviewStage=document`: 문서 타입 (현재는 모든 영상 포함)
   - 생략 또는 `all`: 전체

5. **정렬 옵션:**

   - `sort=latest`: 최신순 (기본값)
   - `sort=oldest`: 오래된순
   - `sort=title`: 제목순 (가나다순)

6. **1차/2차 구분:**

   - 1차: 스크립트 검토 (`SCRIPT_REVIEW_REQUESTED`)
   - 2차: 영상 검토 (`FINAL_REVIEW_REQUESTED`)

7. **승인/반려 처리:**

   - 승인 시 상태가 자동으로 변경됨 (1차: `SCRIPT_APPROVED`, 2차: `PUBLISHED`)
   - 반려 시 반려 사유와 단계가 `EducationVideoReview`에 저장됨

8. **감사 이력:**
   - 영상 생성, 자동 점검, 검토 요청, 승인, 반려 등의 이벤트 기록
   - 제작자 및 리뷰어 정보는 infra-service에서 조회하여 표시

## 관련 API 명세

- [Video API 명세](../../video/api/video_api_spec.md)
- [Education API 명세](../../education/api/education_api_spec.md)
