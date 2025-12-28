# 교육 영상 시청 페이지 플로우

교육 영상 시청 페이지에서 사용되는 API 플로우입니다.

## 1. 페이지 진입 및 영상 정보 조회

```
GET /edu/{id}/videos
→ 교육 제목, 영상 목록, 진행률 정보 조회
→ Response: {
    id,
    title,
    videos: [{
      id,
      title,
      fileUrl,
      duration,
      progressPercent,
      isCompleted,
      resumePosition,
      totalWatchSeconds,
      watchStatus,
      ...
    }]
  }
```

## 2. 영상 시청 중 진행률 업데이트 (주기적으로 요청)

```
POST /edu/{educationId}/video/{videoId}/progress
→ Request: {
    "position": 120,  // 현재 재생 위치(초)
    "watchTime": 120  // 증가 시청시간(초)
  }
→ Response: {
    "updated": true,
    "progress": 7,              // 현재 영상 진행률(%)
    "isCompleted": false,         // 영상 이수 여부
    "totalWatchSeconds": 120,     // 누적 시청 시간
    "eduProgress": 40,            // 교육 전체 진행률(%)
    "eduCompleted": false         // 교육 이수 여부
  }
```

**참고:**

- 영상의 `duration`은 백엔드에서 `EducationVideo` 엔티티에서 자동으로 가져옵니다.
- 영상 완료 여부는 교육의 `passRatio` 기준으로 판단됩니다 (기본값: 100%).
- 교육 전체 진행률은 **PUBLISHED 상태의 영상만** 기준으로 계산됩니다.
- **교육의 모든 PUBLISHED 영상이 시청 완료되면 자동으로 교육 시청 완료 처리됩니다.**

## 3. 시청 완료 (100%) → 자동 교육 이수 처리

영상 시청률이 100%에 도달하면, 영상 시청 중 진행률 업데이트 API 요청하기:

- `isCompleted: true` 반환
- 모든 PUBLISHED 영상이 완료되면 `eduCompleted: true` 반환
- `EducationProgress` 엔티티가 자동으로 업데이트됨

**참고:** `POST /edu/{id}/complete` API는 선택적으로 사용할 수 있지만, `updateVideoProgress`에서 자동으로 처리되므로 수동 호출은 필요하지 않습니다.

> > > 특정 교육에 대한 모든 영상을 시청하면, 퀴즈 풀기 가능

## 4. 퀴즈 풀기 플로우

```
1. GET /quiz/{eduId}/start
   → 퀴즈 시작 (문항 생성/복원)
   → Response: {
       attemptId,
       questions: [
         {
           questionId,
           questionText,
           options: [{ optionId, optionText }],
           ...
         }
       ]
     }

2. [선택] GET /quiz/attempt/{attemptId}/timer
   → 타이머 정보 조회 (시간 제한, 남은 시간)
   → Response: {
       timeLimit,        // 시간 제한(초)
       remainingSeconds, // 남은 시간(초)
       isExpired,        // 만료 여부
       startedAt         // 시작 시각
     }

3. [반복] POST /quiz/attempt/{attemptId}/save
   → 응답 임시 저장 (페이지 새로고침/이탈 시 복구용)
   → Request: {
       answers: [{ questionId, selectedOptionId }]
     }
   → Response: { saved, savedCount }

4. POST /quiz/attempt/{attemptId}/submit
   → 퀴즈 제출 및 채점
   → Request: {
       answers: [{ questionId, selectedOptionId }]
     }
   → Response: {
       score,           // 점수
       passed,          // 통과 여부
       correctCount,    // 정답 개수
       wrongCount,      // 오답 개수
       passScore        // 통과 기준 점수
     }

5. GET /quiz/attempt/{attemptId}/result
   → 퀴즈 결과 조회
   → Response: {
       score,
       passed,
       passScore,
       correctCount,
       wrongCount,
       submittedAt,
       ...
     }
```

## 전체 플로우 다이어그램

```
사용자 → [교육 목록] → [교육 선택] → [영상 시청 페이지]
                                              │
                                              ├─→ GET /edu/{id}/videos (영상 정보 조회)
                                              │
                                              ├─→ POST /edu/{id}/video/{videoId}/progress (진행률 업데이트)
                                              │   └─→ 시청률 100% 도달 시 자동 교육 이수 처리
                                              │
                                              └─→ [퀴즈 풀기 버튼 클릭]
                                                    │
                                                    ├─→ GET /quiz/{eduId}/start (퀴즈 시작)
                                                    │
                                                    ├─→ [선택] GET /quiz/attempt/{attemptId}/timer (타이머 조회)
                                                    │
                                                    ├─→ [반복] POST /quiz/attempt/{attemptId}/save (임시 저장)
                                                    │
                                                    ├─→ POST /quiz/attempt/{attemptId}/submit (퀴즈 제출)
                                                    │
                                                    └─→ GET /quiz/attempt/{attemptId}/result (결과 조회)
```

## 관련 API 명세

- [Education API 명세](../api/education_api_spec.md)
- [Quiz API 명세](../../quiz/api/quiz_api_spec.md)
- [Video API 명세](../../video/api/video_api_spec.md)
- [Script API 명세](../../script/api/script_set_api_spec.md)
- [RAG Documents API 명세](../../../../infra-service/RagAPI.md) (infra-service)

---

# 교육 콘텐츠 제작 페이지 플로우

교육 콘텐츠 제작(CREATOR STUDIO) 페이지에서 사용되는 API 플로우입니다.

1. 교육 영상 컨텐츠 만들기 버튼 클릭시 -> 교육 목록 조회 API로 교육 종류를 클릭하는 모달이 떠서 선택하고 이제 교육 영상 컨텐츠 폼이뜸

```
GET /admin/edus/with-videos
→ 교육 목록과 각 교육의 영상 목록 조회
→ Query Parameter: status (optional) - 영상 상태 필터
→ Response: [
    {
      id: "교육 ID",
      title: "교육 제목",
      videos: [
        {
          id: "영상 ID",
          title: "영상 제목",
          status: "DRAFT" | "SCRIPT_READY" | "SCRIPT_REVIEW_REQUESTED" | ...,
          ...
        }
      ]
    }
  ]
```

**상태별 탭 필터링:**

- **초안**: 영상 상태가 `DRAFT` 또는 `SCRIPT_READY`인 교육
- **검토 대기**: 영상 상태가 `SCRIPT_REVIEW_REQUESTED` 또는 `FINAL_REVIEW_REQUESTED`인 교육
- **반려**: `EducationVideoReview`에 반려 기록이 있는 교육
- **승인**: 영상 상태가 `PUBLISHED`인 교육
- **실패**: 영상 상태가 `FAILED`이거나 에러가 발생한 교육

2. 교육 상세 조회

```
GET /admin/edu/{id}
→ 선택된 교육의 상세 정보 조회
→ Response: {
    id: "교육 ID",
    title: "교육 제목",
    category: "JOB_DUTY" | "SEXUAL_HARASSMENT_PREVENTION" | ...,
    eduType: "MANDATORY" | "JOB" | "ETC",
    require: true | false,
    passScore: 80,
    passRatio: 90,
    createdAt: "2025-12-18T17:31:00Z",
    updatedAt: "2025-12-20T17:31:00Z",
    sections: []
  }
```

3. 새 교육 만들기 (영상 컨텐츠 생성)

```
POST /admin/videos
→ 영상 컨텐츠 생성 (교육과 연결)
→ Request: {
    educationId: "교육 ID",
    title: "영상 제목",
    departmentScope: ["HR", "ENGINEERING"] (optional)
  }
→ Response: {
    videoId: "영상 ID",
    status: "DRAFT"
  }
```

**참고:**

- "새 교육 만들기" 버튼은 영상 컨텐츠를 생성합니다.
- 교육(Education)은 고정되어 있으며, 수정/삭제/추가하지 않습니다.
- 기존 교육에 새로운 영상 컨텐츠를 추가하는 방식입니다.

4. GET /video/{videoId} (선택)
   → 영상 메타 정보 조회
   → 현재 상태 확인 (DRAFT, SCRIPT_READY 등)

5. POST /infra/files/presign/upload [파일 업로드]
   → S3에 파일 업로드 presigned url 받고 s3에 프론트가 직접 업로드 (presigned URL 사용 또는 직접 업로드)
   → 파일 URL 획득 (예: "s3://ctrl-s3/docs/hr_safety_v3.pdf")

6. POST /rag/documents/upload
   → 문서 업로드 메타 등록 (infra-service)
   → Request: {
   title: "문서 제목",
   domain: "HR" | "EDUCATION" | ...,
   fileUrl: "s3://ctrl-s3/docs/hr_safety_v3.pdf"
   }
   → Response: {
   documentId: "문서 ID",
   status: "QUEUED",
   createdAt: "2025-12-27T23:41:00Z"
   }

7. POST /video/source-sets
   → 소스셋 생성 (문서 묶기)
   → Request: {
   videoId: "영상 ID",
   documentIds: ["문서 ID 1", "문서 ID 2", ...]
   }
   → Response: {
   sourceSetId: "소스셋 ID",
   videoId: "영상 ID",
   documentCount: 2,
   status: "PENDING"
   }

```

**참고:**

- 파일은 먼저 S3에 업로드되어야 하며, 그 후 `POST /rag/documents/upload`로 메타 정보 등록
- 소스셋 생성 시 등록된 `documentId` 배열을 전달
- 소스셋 생성 후 AI 서버로 자동 전달되어 스크립트 생성 시작

```

8. 스크립트 생성 요청 (AI 서버 자동 처리)
   → 소스셋 생성 시 AI 서버로 자동 전달
   → 영상 상태: DRAFT → SCRIPT_GENERATING

9. [선택] "스크립트 생성" 버튼 클릭 (수동 재생성)
   → AI 서버로 스크립트 생성 재요청
   → 영상 상태: SCRIPT_READY → SCRIPT_GENERATING

10. 스크립트 생성 완료 대기
    → AI 서버에서 스크립트 생성 완료 콜백 수신
    → 영상 상태: SCRIPT_GENERATING → SCRIPT_READY
    → 스크립트 자동 저장

```

**참고:**

- 스크립트 생성은 비동기로 처리되며, 완료 시 콜백으로 상태 업데이트
- 생성 완료 후 "스크립트 생성 후 씬 편집이 가능합니다" 메시지 표시

```

11. GET /scripts/lookup?videoId={videoId}
    → 영상 ID로 스크립트 ID 조회
    → Response: {
    scriptId: "스크립트 ID",
    videoId: "영상 ID",
    educationId: "교육 ID",
    title: "스크립트 제목",
    version: 1,
    status: "DRAFT" | "REVIEW_REQUESTED" | "APPROVED" | "REJECTED"
    }

12. GET /scripts/{scriptId}
    → 스크립트 상세 조회 (챕터/씬 포함)
    → Response: {
    scriptId: "스크립트 ID",
    educationId: "교육 ID",
    videoId: "영상 ID",
    title: "스크립트 제목",
    totalDurationSec: 720,
    version: 1,
    chapters: [
    {
    chapterId: "챕터 ID",
    index: 0,
    title: "챕터 제목",
    durationSec: 180,
    scenes: [
    {
    sceneId: "씬 ID",
    index: 0,
    text: "씬 텍스트",
    durationSec: 30,
    ...
    }
    ]
    }
    ],
    rawPayload: "{...}"
    }

13. [선택] PUT /scripts/{scriptId}
    → 스크립트 수정 (챕터/씬 전체 교체)
    → Request: {
    rawPayload: "{수정된 JSON}",
    chapters: [
    {
    index: 0,
    title: "수정된 챕터 제목",
    scenes: [
    {
    index: 0,
    text: "수정된 씬 텍스트",
    durationSec: 30
    }
    ]
    }
    ]
    }
    → Response: {
    scriptId: "스크립트 ID",
    updated: true,
    version: 1
    }

```

**참고:**

- 스크립트 생성 완료 후 "미리보기" 섹션에 스크립트 내용 표시
- "스크립트 생성 후 씬 편집이 가능합니다" - 씬 단위로 편집 가능
- 스크립트 수정 시 챕터/씬은 전체 교체 방식

```

14. PUT /admin/videos/{videoId}/review-request
    → 스크립트 검토 요청
    → 영상 상태: SCRIPT_READY → SCRIPT_REVIEW_REQUESTED
    → Response: {
    videoId: "영상 ID",
    previousStatus: "SCRIPT_READY",
    currentStatus: "SCRIPT_REVIEW_REQUESTED",
    changedAt: "2025-12-20T17:31:00Z"
    }

```

```

15. PUT /admin/videos/{videoId}/approve
    → 스크립트 승인 (1차 승인)
    → 영상 상태: SCRIPT_REVIEW_REQUESTED → SCRIPT_APPROVED
    → Response: {
    videoId: "영상 ID",
    previousStatus: "SCRIPT_REVIEW_REQUESTED",
    currentStatus: "SCRIPT_APPROVED",
    changedAt: "2025-12-20T17:31:00Z"
    }

```

**참고:** 1차 승인 후 영상 생성이 가능해집니다.

```

16. 영상 생성 요청 (자동 또는 수동)
    → AI 서버로 영상 생성 요청
    → 영상 상태: SCRIPT_APPROVED → PROCESSING

17. 영상 생성 완료 대기
    → 영상 생성 완료 콜백 수신
    → 영상 상태: PROCESSING → READY

```

### 6.7 최종 검토 요청 (2차)

```

18. PUT /admin/videos/{videoId}/review-request
    → 최종 검토 요청 (2차)
    → 영상 상태: READY → FINAL_REVIEW_REQUESTED
    → Response: {
    videoId: "영상 ID",
    previousStatus: "READY",
    currentStatus: "FINAL_REVIEW_REQUESTED",
    changedAt: "2025-12-20T17:31:00Z"
    }

```

**참고:** "최종 검토 요청(2차)" 버튼 클릭 시 호출됩니다.

```

19. PUT /admin/videos/{videoId}/approve
    → 최종 승인 (2차 승인 = 게시)
    → 영상 상태: FINAL_REVIEW_REQUESTED → PUBLISHED
    → Response: {
    videoId: "영상 ID",
    previousStatus: "FINAL_REVIEW_REQUESTED",
    currentStatus: "PUBLISHED",
    changedAt: "2025-12-20T17:31:00Z"
    }

```

**참고:** 2차 승인 시 자동으로 게시(PUBLISHED) 처리되어 교육이 노출됩니다.


```

19-1. 반되려는 경우, PUT /admin/videos/{videoId}/reject
→ 검토 반려
→ Request: {
reason: "반려 사유"
}
→ 1차 반려: SCRIPT_REVIEW_REQUESTED → SCRIPT_READY
→ 2차 반려: FINAL_REVIEW_REQUESTED → READY
→ Response: {
videoId: "영상 ID",
previousStatus: "SCRIPT_REVIEW_REQUESTED" | "FINAL_REVIEW_REQUESTED",
currentStatus: "SCRIPT_READY" | "READY",
changedAt: "2025-12-20T17:31:00Z"
}

```

**참고:** 반려 사유는 `EducationVideoReview` 테이블에 저장됩니다.

## 전체 워크플로우 다이어그램

```

[교육 콘텐츠 제작 페이지]
│
├─→ GET /admin/edus/with-videos (교육 목록 조회)
│
├─→ GET /admin/edu/{id} (교육 상세 조회)
│
├─→ [새 교육 만들기]
│ └─→ POST /admin/videos (영상 컨텐츠 생성)
│
└─→ [콘텐츠 제작 워크플로우]
│
├─→ [1단계] 자료 업로드
│ ├─→ GET /admin/edu/{id} (교육 상세 조회)
│ ├─→ GET /video/{videoId} (영상 정보 조회)
│ ├─→ [파일 업로드] S3에 파일 업로드
│ ├─→ POST /rag/documents/upload (문서 메타 등록, infra-service)
│ ├─→ POST /video/source-sets (소스셋 생성)
│ └─→ [선택] PATCH /video/source-sets/{sourceSetId} (문서 추가/제거)
│
├─→ [2단계] 스크립트 생성
│ ├─→ AI 서버로 자동 전달 (소스셋 생성 시)
│ ├─→ [선택] "스크립트 생성" 버튼 클릭 (수동 재생성)
│ └─→ 상태: DRAFT → SCRIPT_GENERATING → SCRIPT_READY
│
├─→ [3단계] 스크립트 조회 및 편집
│ ├─→ GET /scripts/lookup?videoId={videoId} (스크립트 ID 조회)
│ ├─→ GET /scripts/{scriptId} (스크립트 상세 조회)
│ └─→ [선택] PUT /scripts/{scriptId} (스크립트 수정)
│
├─→ [4단계] 스크립트 검토 요청 (1차)
│ └─→ PUT /admin/videos/{videoId}/review-request
│ └─→ 상태: SCRIPT_READY → SCRIPT_REVIEW_REQUESTED
│
├─→ [5단계] 1차 승인 (스크립트 승인)
│ └─→ PUT /admin/videos/{videoId}/approve
│ └─→ 상태: SCRIPT_REVIEW_REQUESTED → SCRIPT_APPROVED
│
├─→ [6단계] 영상 생성 (자동)
│ └─→ 상태: SCRIPT_APPROVED → PROCESSING → READY
│
├─→ [7단계] 최종 검토 요청 (2차)
│ └─→ PUT /admin/videos/{videoId}/review-request
│ └─→ 상태: READY → FINAL_REVIEW_REQUESTED
│
└─→ [8단계] 2차 승인 (게시)
└─→ PUT /admin/videos/{videoId}/approve
└─→ 상태: FINAL_REVIEW_REQUESTED → PUBLISHED

```

## 진행 단계별 상태

| 단계                  | 영상 상태                                 | 설명                                 |
| --------------------- | ----------------------------------------- | ------------------------------------ |
| 1. 자료 업로드        | DRAFT (소스셋 생성)                       | 소스셋 생성 및 문서 업로드           |
| 2. 스크립트 생성      | DRAFT → SCRIPT_GENERATING → SCRIPT_READY  | AI 서버로 스크립트 생성 요청 및 완료 |
| 3. 스크립트 조회/편집 | SCRIPT_READY (유지)                       | 스크립트 상세 조회 및 씬 편집        |
| 4. 스크립트 검토 요청 | SCRIPT_READY → SCRIPT_REVIEW_REQUESTED    | 1차 검토 요청                        |
| 5. 1차 승인           | SCRIPT_REVIEW_REQUESTED → SCRIPT_APPROVED | 스크립트 승인, 영상 생성 가능        |
| 6. 영상 생성          | SCRIPT_APPROVED → PROCESSING → READY      | 영상 생성 중 → 생성 완료             |
| 7. 최종 검토 요청     | READY → FINAL_REVIEW_REQUESTED            | 2차 검토 요청                        |
| 8. 2차 승인 (게시)    | FINAL_REVIEW_REQUESTED → PUBLISHED        | 최종 승인 = 게시 완료                |

## 참고사항

1. **상태 필터링**: `GET /admin/edus/with-videos?status={VideoStatus}`로 특정 상태의 영상만 필터링할 수 있습니다.
2. **자동 처리**: 스크립트 생성과 영상 생성은 AI 서버에서 비동기로 처리되며, 완료 시 콜백으로 상태가 업데이트됩니다.
3. **반려 처리**: 반려 시 `EducationVideoReview`에 반려 사유와 단계(SCRIPT/VIDEO)가 저장됩니다.
4. **게시**: 2차 승인 시 자동으로 PUBLISHED 상태가 되어 교육이 노출됩니다.
5. **자료 업로드**:
   - 파일은 먼저 S3에 업로드되어야 합니다
   - `POST /rag/documents/upload` (infra-service)로 문서 메타 정보를 등록하여 `documentId`를 획득합니다
   - 소스셋 생성 시 등록된 `documentId` 배열을 전달합니다
6. **스크립트 편집**: 스크립트 수정 시 챕터/씬은 전체 교체 방식이며, 개별 씬만 수정하려면 전체 챕터를 다시 전송해야 합니다.
7. **스크립트 검토 요청 전 체크**: 스크립트가 생성되어 있어야만 검토 요청이 가능합니다. (상태: SCRIPT_READY)
```
