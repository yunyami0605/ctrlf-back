## Quiz Service API (문서 기준)

Base URL: http://localhost:9002

---

# 1. Quiz

## 1.1 풀 수 있는 퀴즈 목록 조회

### ✔ URL

- GET /quiz/available-educations

### ✔ 설명

- 이수 완료한 교육만 리스트에 노출합니다.
- 각 교육에 대한 응시 횟수, 최고 점수, 통과 여부 정보를 포함합니다.

### ✔ 권한

`ROLE_USER`

### ✔ 요청

Body: 없음

### Query Parameter

없음

### Response

array of object

| key          | 설명                      | value 타입   | 옵션     | Nullable | 예시                   |
| ------------ | ------------------------- | ------------ | -------- | -------- | ---------------------- |
| educationId  | 교육 ID                   | string(uuid) | required | false    | "550e..."              |
| title        | 교육 제목                 | string       | required | false    | "산업안전 교육"        |
| category     | 카테고리                  | string       | required | false    | "MANDATORY"            |
| eduType      | 교육 유형                 | string       | required | false    | "JOB"                  |
| attemptCount | 기존 응시 횟수            | number       | required | false    | 2                      |
| maxAttempts  | 최대 응시 횟수            | number       | optional | true     | 3 (null이면 무제한)    |
| hasAttempted | 이미 응시한 퀴즈인지 여부 | boolean      | required | false    | true                   |
| bestScore    | 최고 점수                 | number       | optional | true     | 85 (null이면 미응시)   |
| passed       | 통과 여부                 | boolean      | optional | true     | true (null이면 미응시) |

### Status

| status  | response content |
| ------- | ---------------- |
| 200 OK  | 정상             |
| 401/403 | 인증/권한 오류   |

---

## 1.2 퀴즈 시작(문항 생성/복원)

### ✔ URL

- GET /quiz/{eduId}/start

### ✔ 설명

- 새로운 퀴즈 시도를 생성(AI 서버에서 문항을 생성)
- 복원 시 저장된 답안을 포함하여 반환합니다.

### ✔ 권한

`ROLE_USER`

### ✔ 요청

Body: 없음

### Path

- eduId: UUID (교육 ID)

### Response

| key       | 설명      | value 타입    | 옵션     | Nullable | 예시         |
| --------- | --------- | ------------- | -------- | -------- | ------------ |
| attemptId | 시도 ID   | string(uuid)  | required | false    | "550e..."    |
| questions | 문항 목록 | array(object) | required | false    | 아래 표 참조 |

questions item

| key         | 설명                | value 타입    | 옵션     | Nullable | 예시                       |
| ----------- | ------------------- | ------------- | -------- | -------- | -------------------------- |
| questionId  | 문항 ID             | string(uuid)  | required | false    | "550e..."                  |
| order       | 문항 순서 (0-based) | number        | required | false    | 0                          |
| question    | 문제 내용           | string        | required | false    | "산업안전의 목적은?"       |
| choices     | 선택지 목록         | array(string) | required | false    | ["안전", "효율", "생산성"] |
| answerIndex | 정답 인덱스         | number        | optional | true     | 0 (제출 전에는 null)       |

### Status

| status        | response content    |
| ------------- | ------------------- |
| 200 OK        | 정상                |
| 404 Not Found | 교육을 찾을 수 없음 |
| 401/403       | 인증/권한 오류      |

---

## 1.3 응답 임시 저장

### ✔ URL

- POST /quiz/attempt/{attemptId}/save

### ✔ 설명

- 진행 중인 답안을 임시 저장합니다 (페이지 새로고침/이탈 시 복구용).
- 저장된 답안은 퀴즈 시작 시 자동으로 복원됩니다.

### ✔ 권한

`ROLE_USER`

### ✔ 요청

Body: 있음

### Path

- attemptId: UUID (시도 ID)

### Request

| key     | 설명                  | value 타입    | 옵션     | Nullable | 예시         |
| ------- | --------------------- | ------------- | -------- | -------- | ------------ |
| answers | 임시 저장할 답안 목록 | array(object) | required | false    | 아래 표 참조 |

answers item

| key               | 설명                   | value 타입   | 옵션     | Nullable | 예시      |
| ----------------- | ---------------------- | ------------ | -------- | -------- | --------- |
| questionId        | 문항 ID                | string(uuid) | required | false    | "550e..." |
| userSelectedIndex | 사용자가 선택한 인덱스 | number       | required | false    | 0         |

### Response

| key        | 설명             | value 타입       | 옵션     | Nullable | 예시                   |
| ---------- | ---------------- | ---------------- | -------- | -------- | ---------------------- |
| saved      | 저장 성공 여부   | boolean          | required | false    | true                   |
| savedCount | 저장된 답안 개수 | number           | required | false    | 5                      |
| savedAt    | 저장 시각        | string(ISO-8601) | required | false    | "2025-12-24T10:00:00Z" |

### Status

| status        | response content    |
| ------------- | ------------------- |
| 200 OK        | 정상                |
| 404 Not Found | 시도를 찾을 수 없음 |
| 401/403       | 인증/권한 오류      |

---

## 1.4 타이머 정보 조회

### ✔ URL

- GET /quiz/attempt/{attemptId}/timer

### ✔ 설명

- 시간 제한, 남은 시간, 시작 시각을 조회합니다.

### ✔ 권한

`ROLE_USER`

### ✔ 요청

Body: 없음

### Path

- attemptId: UUID (시도 ID)

### Response

| key              | 설명          | value 타입       | 옵션     | Nullable | 예시                                        |
| ---------------- | ------------- | ---------------- | -------- | -------- | ------------------------------------------- |
| timeLimit        | 시간 제한(초) | number           | optional | true     | 900 (null이면 제한 없음)                    |
| startedAt        | 시작 시각     | string(ISO-8601) | required | false    | "2025-12-24T10:00:00Z"                      |
| expiresAt        | 만료 시각     | string(ISO-8601) | optional | true     | "2025-12-24T10:15:00Z" (null이면 제한 없음) |
| remainingSeconds | 남은 시간(초) | number           | optional | true     | 600 (null이면 제한 없음 또는 이미 만료)     |
| isExpired        | 만료 여부     | boolean          | required | false    | false                                       |

### Status

| status        | response content    |
| ------------- | ------------------- |
| 200 OK        | 정상                |
| 404 Not Found | 시도를 찾을 수 없음 |
| 401/403       | 인증/권한 오류      |

---

## 1.5 퀴즈 제출/채점

### ✔ URL

- POST /quiz/attempt/{attemptId}/submit

### ✔ 설명

- 퀴즈를 제출하고 자동으로 채점합니다.
- 점수, 통과 여부, 정답/오답 개수를 반환합니다.

### ✔ 권한

`ROLE_USER`

### ✔ 요청

Body: 있음

### Path

- attemptId: UUID (시도 ID)

### Request

| key     | 설명      | value 타입    | 옵션     | Nullable | 예시         |
| ------- | --------- | ------------- | -------- | -------- | ------------ |
| answers | 답안 목록 | array(object) | required | false    | 아래 표 참조 |

answers item

| key               | 설명                   | value 타입   | 옵션     | Nullable | 예시      |
| ----------------- | ---------------------- | ------------ | -------- | -------- | --------- |
| questionId        | 문항 ID                | string(uuid) | required | false    | "550e..." |
| userSelectedIndex | 사용자가 선택한 인덱스 | number       | required | false    | 0         |

### Response

| key          | 설명         | value 타입       | 옵션     | Nullable | 예시                   |
| ------------ | ------------ | ---------------- | -------- | -------- | ---------------------- |
| score        | 점수         | number           | required | false    | 85                     |
| passed       | 통과 여부    | boolean          | required | false    | true                   |
| correctCount | 정답 개수    | number           | required | false    | 17                     |
| wrongCount   | 오답 개수    | number           | required | false    | 3                      |
| totalCount   | 전체 문항 수 | number           | required | false    | 20                     |
| submittedAt  | 제출 시각    | string(ISO-8601) | required | false    | "2025-12-24T10:15:00Z" |

### Status

| status          | response content              |
| --------------- | ----------------------------- |
| 200 OK          | 정상                          |
| 404 Not Found   | 시도를 찾을 수 없음           |
| 400 Bad Request | 잘못된 요청 (예: 이미 제출됨) |
| 401/403         | 인증/권한 오류                |

---

## 1.6 퀴즈 결과 조회

### ✔ URL

- GET /quiz/attempt/{attemptId}/result

### ✔ 설명

- 제출된 퀴즈의 결과를 조회합니다.

### ✔ 권한

`ROLE_USER`

### ✔ 요청

Body: 없음

### Path

- attemptId: UUID (시도 ID)

### Response

| key          | 설명           | value 타입       | 옵션     | Nullable | 예시                         |
| ------------ | -------------- | ---------------- | -------- | -------- | ---------------------------- |
| score        | 점수           | number           | required | false    | 85                           |
| passed       | 통과 여부      | boolean          | required | false    | true                         |
| passScore    | 통과 기준 점수 | number           | optional | true     | 80 (null이면 전체 정답 기준) |
| correctCount | 정답 개수      | number           | required | false    | 17                           |
| wrongCount   | 오답 개수      | number           | required | false    | 3                            |
| totalCount   | 전체 문항 수   | number           | required | false    | 20                           |
| finishedAt   | 완료 시각      | string(ISO-8601) | required | false    | "2025-12-24T10:15:00Z"       |

### Status

| status        | response content    |
| ------------- | ------------------- |
| 200 OK        | 정상                |
| 404 Not Found | 시도를 찾을 수 없음 |
| 401/403       | 인증/권한 오류      |

---

## 1.7 오답노트 목록 조회

### ✔ URL

- GET /quiz/{attemptId}/wrongs

### ✔ 설명

- 제출된 퀴즈의 오답 목록을 조회합니다.
- 각 오답에 대한 문제, 사용자 답안, 정답, 해설을 포함합니다.

### ✔ 권한

`ROLE_USER`

### ✔ 요청

Body: 없음

### Path

- attemptId: UUID (시도 ID)

### Response

array of object

| key                | 설명                   | value 타입    | 옵션     | Nullable | 예시                       |
| ------------------ | ---------------------- | ------------- | -------- | -------- | -------------------------- |
| question           | 문제 내용              | string        | required | false    | "산업안전의 목적은?"       |
| userAnswerIndex    | 사용자가 선택한 인덱스 | number        | required | false    | 1                          |
| correctAnswerIndex | 정답 인덱스            | number        | required | false    | 0                          |
| explanation        | 해설                   | string        | required | false    | "산업안전의 목적은..."     |
| choices            | 선택지 목록            | array(string) | required | false    | ["안전", "효율", "생산성"] |

### Status

| status        | response content    |
| ------------- | ------------------- |
| 200 OK        | 정상                |
| 404 Not Found | 시도를 찾을 수 없음 |
| 401/403       | 인증/권한 오류      |

---

## 1.8 퀴즈 이탈 기록

### ✔ URL

- POST /quiz/attempt/{attemptId}/leave

### ✔ 설명

- 퀴즈 진행 중 이탈(탭 전환, 창 닫기 등)을 기록합니다.
- 부정행위 방지를 위한 이탈 추적 기능입니다.

### ✔ 권한

`ROLE_USER`

### ✔ 요청

Body: 있음

### Path

- attemptId: UUID (시도 ID)

### Request

| key          | 설명          | value 타입       | 옵션     | Nullable | 예시                   |
| ------------ | ------------- | ---------------- | -------- | -------- | ---------------------- |
| timestamp    | 이탈 시각     | string(ISO-8601) | required | false    | "2025-12-24T10:10:00Z" |
| reason       | 이탈 사유     | string           | required | false    | "window_blur"          |
| leaveSeconds | 이탈 시간(초) | number           | optional | true     | 30                     |

### Response

| key         | 설명             | value 타입       | 옵션     | Nullable | 예시                   |
| ----------- | ---------------- | ---------------- | -------- | -------- | ---------------------- |
| recorded    | 기록 성공 여부   | boolean          | required | false    | true                   |
| leaveCount  | 총 이탈 횟수     | number           | required | false    | 2                      |
| lastLeaveAt | 마지막 이탈 시각 | string(ISO-8601) | required | false    | "2025-12-24T10:10:00Z" |

### Status

| status        | response content    |
| ------------- | ------------------- |
| 200 OK        | 정상                |
| 404 Not Found | 시도를 찾을 수 없음 |
| 401/403       | 인증/권한 오류      |

---

## 1.9 내가 풀었던 퀴즈 응시 내역 조회

### ✔ URL

- GET /quiz/my-attempts

### ✔ 설명

- 응시한 퀴즈 목록, 점수, 통과 여부, 교육별 최고 점수를 조회합니다.

### ✔ 권한

`ROLE_USER`

### ✔ 요청

Body: 없음

### Query Parameter

없음

### Response

array of object

| key            | 설명                  | value 타입       | 옵션     | Nullable | 예시                   |
| -------------- | --------------------- | ---------------- | -------- | -------- | ---------------------- |
| attemptId      | 시도 ID               | string(uuid)     | required | false    | "550e..."              |
| educationId    | 교육 ID               | string(uuid)     | required | false    | "550e..."              |
| educationTitle | 교육 제목             | string           | required | false    | "산업안전 교육"        |
| score          | 점수                  | number           | required | false    | 85                     |
| passed         | 통과 여부             | boolean          | required | false    | true                   |
| attemptNo      | 응시 횟수             | number           | required | false    | 1                      |
| submittedAt    | 제출 시각             | string(ISO-8601) | required | false    | "2025-12-24T10:15:00Z" |
| isBestScore    | 교육별 최고 점수 여부 | boolean          | required | false    | true                   |

### Status

| status  | response content |
| ------- | ---------------- |
| 200 OK  | 정상             |
| 401/403 | 인증/권한 오류   |

---

## 1.10 퀴즈 재응시 정보 조회

### ✔ URL

- GET /quiz/{eduId}/retry-info

### ✔ 설명

- 특정 교육에 대한 재응시 가능 여부 및 관련 정보를 조회합니다.
- 응시 횟수, 최고 점수, 통과 여부 등을 포함합니다.

### ✔ 권한

`ROLE_USER`

### ✔ 요청

Body: 없음

### Path

- eduId: UUID (교육 ID)

### Response

| key                 | 설명             | value 타입       | 옵션     | Nullable | 예시                       |
| ------------------- | ---------------- | ---------------- | -------- | -------- | -------------------------- |
| educationId         | 교육 ID          | string(uuid)     | required | false    | "550e..."                  |
| educationTitle      | 교육 제목        | string           | required | false    | "산업안전 교육"            |
| canRetry            | 재응시 가능 여부 | boolean          | required | false    | true                       |
| currentAttemptCount | 현재 응시 횟수   | number           | required | false    | 2                          |
| maxAttempts         | 최대 응시 횟수   | number           | optional | true     | 3 (null이면 무제한)        |
| remainingAttempts   | 남은 응시 횟수   | number           | optional | true     | 1 (null이면 무제한)        |
| bestScore           | 최고 점수        | number           | optional | true     | 85 (응시한 경우)           |
| passed              | 통과 여부        | boolean          | optional | true     | true (최고 점수 시도 기준) |
| lastAttemptAt       | 마지막 응시 시각 | string(ISO-8601) | optional | true     | "2025-12-24T10:15:00Z"     |

### Status

| status        | response content    |
| ------------- | ------------------- |
| 200 OK        | 정상                |
| 404 Not Found | 교육을 찾을 수 없음 |
| 401/403       | 인증/권한 오류      |

---

## 1.11 부서별 퀴즈 통계 조회

### ✔ URL

- GET /quiz/department-stats

### ✔ 설명

- 부서별 평균 점수와 진행률을 조회합니다.
- educationId가 없으면 전체 교육 대상으로 계산합니다.

### ✔ 권한

`ROLE_USER` (또는 `ROLE_ADMIN`)

### ✔ 요청

Body: 없음

### Query Parameter

| key         | 설명    | value 타입   | 옵션     | Nullable | 예시      |
| ----------- | ------- | ------------ | -------- | -------- | --------- |
| educationId | 교육 ID | string(uuid) | optional | true     | "550e..." |

### Response

array of object

| key              | 설명                 | value 타입 | 옵션     | Nullable | 예시     |
| ---------------- | -------------------- | ---------- | -------- | -------- | -------- |
| departmentName   | 부서명               | string     | required | false    | "인사팀" |
| averageScore     | 부서 평균 점수       | number     | required | false    | 85       |
| progressPercent  | 부서 전체 진행률 (%) | number     | required | false    | 75       |
| participantCount | 참여자 수            | number     | required | false    | 50       |

### Status

| status  | response content |
| ------- | ---------------- |
| 200 OK  | 정상             |
| 401/403 | 인증/권한 오류   |

---

# 2. Quiz Dashboard Statistics (Admin)

## 2.1 대시보드 요약 통계 조회

### ✔ URL

- GET /admin/dashboard/quiz/summary

### ✔ 설명

- 전체 평균 점수, 응시자 수, 통과율(80점↑), 퀴즈 응시율을 조회합니다.
- 기간 필터(period)와 부서 필터(department)를 지원합니다.

### ✔ 권한

`ROLE_ADMIN`

### ✔ 요청

Body: 없음

### Query Parameter

| key        | 설명                    | value 타입 | 옵션     | Nullable | 예시   |
| ---------- | ----------------------- | ---------- | -------- | -------- | ------ |
| period     | 기간 (일수, 7/30/90)    | number     | optional | true     | 30     |
| department | 부서 필터               | string     | optional | true     | "총무팀" |

### Response

| key                | 설명                | value 타입 | 옵션     | Nullable | 예시  |
| ------------------ | ------------------- | ---------- | -------- | -------- | ----- |
| overallAverageScore | 전체 평균 점수      | number     | required | false    | 84.0   |
| participantCount   | 응시자 수           | number     | required | false    | 176    |
| passRate           | 통과율 (80점↑) (%)  | number     | required | false    | 78.0   |
| participationRate  | 퀴즈 응시율 (%)     | number     | required | false    | 73.0   |

### Status

| status  | response content |
| ------- | ---------------- |
| 200 OK  | 정상             |
| 401/403 | 인증/권한 오류   |

---

## 2.2 부서별 평균 점수 조회

### ✔ URL

- GET /admin/dashboard/quiz/department-scores

### ✔ 설명

- 부서별 평균 점수와 응시자 수를 조회합니다.
- 기간 필터(period)와 부서 필터(department)를 지원합니다.
- 평균 점수 기준으로 내림차순 정렬됩니다.

### ✔ 권한

`ROLE_ADMIN`

### ✔ 요청

Body: 없음

### Query Parameter

| key        | 설명                    | value 타입 | 옵션     | Nullable | 예시   |
| ---------- | ----------------------- | ---------- | -------- | -------- | ------ |
| period     | 기간 (일수, 7/30/90)    | number     | optional | true     | 30     |
| department | 부서 필터               | string     | optional | true     | "총무팀" |

### Response

array of object

| key              | 설명          | value 타입 | 옵션     | Nullable | 예시   |
| ---------------- | ------------- | ---------- | -------- | -------- | ------ |
| department       | 부서명        | string     | required | false    | "인사팀" |
| averageScore     | 평균 점수     | number     | required | false    | 89.0   |
| participantCount | 응시자 수     | number     | required | false    | 13     |

### Status

| status  | response content |
| ------- | ---------------- |
| 200 OK  | 정상             |
| 401/403 | 인증/권한 오류   |

---

## 2.3 퀴즈별 통계 조회

### ✔ URL

- GET /admin/dashboard/quiz/quiz-stats

### ✔ 설명

- 퀴즈 제목, 회차, 평균 점수, 응시 수, 통과율을 조회합니다.
- 기간 필터(period)와 부서 필터(department)를 지원합니다.
- 평균 점수 기준으로 내림차순 정렬됩니다.

### ✔ 권한

`ROLE_ADMIN`

### ✔ 요청

Body: 없음

### Query Parameter

| key        | 설명                    | value 타입 | 옵션     | Nullable | 예시   |
| ---------- | ----------------------- | ---------- | -------- | -------- | ------ |
| period     | 기간 (일수, 7/30/90)    | number     | optional | true     | 30     |
| department | 부서 필터               | string     | optional | true     | "총무팀" |

### Response

array of object

| key          | 설명           | value 타입   | 옵션     | Nullable | 예시                                   |
| ------------ | -------------- | ------------ | -------- | -------- | -------------------------------------- |
| educationId  | 교육 ID        | string(uuid) | required | false    | "550e8400-e29b-41d4-a716-446655440000" |
| quizTitle    | 퀴즈 제목      | string       | required | false    | "개인정보보호 퀴즈"                     |
| attemptNo    | 회차           | number       | required | false    | 1                                      |
| averageScore | 평균 점수      | number       | required | false    | 86.0                                   |
| attemptCount | 응시 수        | number       | required | false    | 57                                     |
| passRate     | 통과율 (%)     | number       | required | false    | 81.0                                   |

### Status

| status  | response content |
| ------- | ---------------- |
| 200 OK  | 정상             |
| 401/403 | 인증/권한 오류   |

---

## 주의사항

1. **시간 제한**: 새 퀴즈 시도 생성 시 기본 시간 제한은 15분(900초)입니다. 단, 시간 제한은 타이머 정보 조회용이며, 백엔드에서 제출을 강제하지는 않습니다. 프론트엔드에서 타이머를 표시하고 만료 시 제출을 막을 수 있습니다.
2. **임시 저장**: 답안은 임시 저장 가능하며, 퀴즈 시작 시 자동으로 복원됩니다.
3. **재응시**: 최대 응시 횟수가 설정된 경우, 횟수 제한을 초과하면 재응시가 불가능합니다.
4. **이탈 추적**: 퀴즈 진행 중 이탈은 자동으로 기록되며, 부정행위 방지를 위한 기능입니다.
5. **AI 문항 생성**: 퀴즈 시작 시 AI 서버에서 문항을 생성하며, 실패 시 placeholder 문항으로 폴백합니다.

---

## 관련 문서

- [Education API 명세](../education/api/education_api_spec.md)
- [Video API 명세](../video/api/video_api_spec.md)
