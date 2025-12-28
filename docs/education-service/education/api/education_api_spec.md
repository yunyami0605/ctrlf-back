## Education Service API (문서 기준)

Base URL: http://localhost:9002

---

# 1. Education (Admin)

## 1.1 교육 생성 (\* 개발용)

### ✔ URL

- POST /admin/edu

### ✔ 설명

- 교육을 생성합니다.

### ✔ 권한

`ROLE_ADMIN`

### ✔ 요청

Body: 있음

### Request

| key         | 설명              | value 타입 | 옵션     | Nullable | 예시                                                                                                                    |
| ----------- | ----------------- | ---------- | -------- | -------- | ----------------------------------------------------------------------------------------------------------------------- |
| title       | 교육 제목         | string     | required | false    | "산업안전 교육"                                                                                                         |
| description | 설명              | string     | optional | true     | "산업안전 수칙"                                                                                                         |
| category    | 주제 카테고리     | string     | required | false    | "JOB_DUTY" (JOB_DUTY, SEXUAL_HARASSMENT_PREVENTION, PERSONAL_INFO_PROTECTION, WORKPLACE_BULLYING, DISABILITY_AWARENESS) |
| eduType     | 교육 유형         | string     | optional | true     | "MANDATORY" (MANDATORY, JOB, ETC)                                                                                       |
| require     | 필수 여부         | boolean    | required | false    | true                                                                                                                    |
| passScore   | 통과 기준 점수    | number     | optional | true     | 80                                                                                                                      |
| passRatio   | 통과 기준 비율(%) | number     | optional | true     | 90                                                                                                                      |

### Query Parameter

없음

### Response

| key | 설명           | value 타입   | 옵션     | Nullable | 예시                                   |
| --- | -------------- | ------------ | -------- | -------- | -------------------------------------- |
| id  | 생성된 교육 ID | string(uuid) | required | false    | "550e8400-e29b-41d4-a716-446655440000" |

### Status

| status          | response content |
| --------------- | ---------------- |
| 201 Created     | 정상 생성        |
| 400 Bad Request | 유효성 실패      |
| 401/403         | 인증/권한 오류   |

---

## 1.2 교육 상세 조회 (\* 개발용)

### ✔ URL

- GET /admin/edu/{id}

### ✔ 설명

- 교육 기본 정보와 차시 정보를 조회합니다.

### ✔ 권한

`ROLE_USER`

### ✔ 요청

Body: 없음

### Path

- id: UUID (교육 ID)

### Response

| key         | 설명              | value 타입       | 옵션     | Nullable | 예시                                   |
| ----------- | ----------------- | ---------------- | -------- | -------- | -------------------------------------- |
| id          | 교육 ID           | string(uuid)     | required | false    | "550e8400-e29b-41d4-a716-446655440000" |
| title       | 제목              | string           | required | false    | "산업안전 교육"                        |
| description | 설명              | string           | optional | true     | "산업안전 수칙"                        |
| category    | 주제 카테고리     | string           | required | false    | "JOB_DUTY"                             |
| eduType     | 교육 유형         | string           | optional | true     | "MANDATORY"                            |
| require     | 필수 여부         | boolean          | optional | true     | true                                   |
| passScore   | 통과 기준 점수    | number           | optional | true     | 80                                     |
| passRatio   | 통과 기준 비율(%) | number           | optional | true     | 90                                     |
| duration    | 총 길이(초)       | number           | required | false    | 3600                                   |
| createdAt   | 생성 시각         | string(ISO-8601) | optional | true     | "2025-12-17T10:00:00Z"                 |
| updatedAt   | 수정 시각         | string(ISO-8601) | optional | true     | "2025-12-17T10:00:00Z"                 |
| sections    | 섹션 목록         | array(object)    | required | false    | 아래 표 참조                           |

sections item

| key   | 설명      | value 타입   | 옵션     | Nullable | 예시                                   |
| ----- | --------- | ------------ | -------- | -------- | -------------------------------------- |
| id    | 섹션 ID   | string(uuid) | required | false    | "550e8400-e29b-41d4-a716-446655440001" |
| title | 섹션 제목 | string       | required | false    | "1차시: 안전 수칙"                     |

### Status

| status        | response content |
| ------------- | ---------------- |
| 200 OK        | 정상             |
| 404 Not Found | 없음             |
| 401/403       | 인증/권한 오류   |

---

## 1.3 교육 수정 (\* 개발용)

### ✔ URL

- PUT /admin/edu/{id}

### ✔ 설명

- 교육 정보를 부분 업데이트합니다.

### ✔ 권한

`ROLE_ADMIN`

### ✔ 요청

Body: 있음

### Path

- id: UUID (교육 ID)

### Request

| key         | 설명              | value 타입 | 옵션     | Nullable | 예시                  |
| ----------- | ----------------- | ---------- | -------- | -------- | --------------------- |
| title       | 교육 제목         | string     | optional | true     | "산업안전 교육(개정)" |
| description | 설명              | string     | optional | true     | "수칙 개정"           |
| category    | 주제 카테고리     | string     | optional | true     | "JOB_DUTY"            |
| eduType     | 교육 유형         | string     | optional | true     | "MANDATORY"           |
| require     | 필수 여부         | boolean    | optional | true     | true                  |
| passScore   | 통과 기준 점수    | number     | optional | true     | 85                    |
| passRatio   | 통과 기준 비율(%) | number     | optional | true     | 95                    |

### Response

| key       | 설명          | value 타입   | 옵션     | Nullable | 예시                                   |
| --------- | ------------- | ------------ | -------- | -------- | -------------------------------------- |
| eduId     | 교육 ID       | string(uuid) | required | false    | "550e8400-e29b-41d4-a716-446655440000" |
| updated   | 업데이트 여부 | boolean      | required | false    | true                                   |
| updatedAt | ISO-8601      | string       | required | false    | "2025-12-17T10:00:00Z"                 |

### Status

| status          | response content    |
| --------------- | ------------------- |
| 200 OK          | 정상                |
| 400 Bad Request | 유효성 실패         |
| 404 Not Found   | 교육을 찾을 수 없음 |
| 401/403         | 인증/권한 오류      |

---

## 1.4 교육 삭제 (\* 개발용)

### ✔ URL

- DELETE /admin/edu/{id}

### ✔ 설명

- 교육을 삭제합니다.

### ✔ 권한

`ROLE_ADMIN`

### ✔ 요청

Body: 없음

### Path

- id: UUID (교육 ID)

### Response

| key    | 설명           | value 타입   | 옵션     | Nullable | 예시                                   |
| ------ | -------------- | ------------ | -------- | -------- | -------------------------------------- |
| eduId  | 삭제된 교육 ID | string(uuid) | required | false    | "550e8400-e29b-41d4-a716-446655440000" |
| status | 상태           | string       | required | false    | "DELETED"                              |

### Status

| status        | response content    |
| ------------- | ------------------- |
| 200 OK        | 정상                |
| 404 Not Found | 교육을 찾을 수 없음 |
| 401/403       | 인증/권한 오류      |

---

## 1.5 전체 교육 + 영상 목록 조회

### ✔ URL

- GET /admin/edus/with-videos

### ✔ 설명

- 모든 교육을 조회하고 각 교육에 포함된 영상 목록을 함께 반환합니다(사용자 진행 정보 제외).
- status 파라미터로 특정 상태의 영상만 필터링할 수 있습니다.

### ✔ 권한

`ROLE_ADMIN`

### ✔ 요청

Body: 없음

### Query Parameter

| key    | 설명           | value 타입 | 옵션     | Nullable | 예시                                                                                                                                        |
| ------ | -------------- | ---------- | -------- | -------- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| status | 영상 상태 필터 | string     | optional | true     | "PUBLISHED" (DRAFT, SCRIPT_READY, SCRIPT_REVIEW_REQUESTED, SCRIPT_APPROVED, PROCESSING, READY, FINAL_REVIEW_REQUESTED, PUBLISHED, DISABLED) |

### Response

array of object

| key    | 설명      | value 타입    | 옵션     | Nullable | 예시                                   |
| ------ | --------- | ------------- | -------- | -------- | -------------------------------------- |
| id     | 교육 ID   | string(uuid)  | required | false    | "550e8400-e29b-41d4-a716-446655440000" |
| title  | 교육 제목 | string        | required | false    | "산업안전 교육"                        |
| videos | 영상 목록 | array(object) | required | false    | 아래 표 참조                           |

videos item

| key             | 설명                              | value 타입   | 옵션     | Nullable | 예시                                           |
| --------------- | --------------------------------- | ------------ | -------- | -------- | ---------------------------------------------- |
| id              | 영상 ID                           | string(uuid) | required | false    | "550e8400-e29b-41d4-a716-446655440001"         |
| title           | 영상 제목                         | string       | required | false    | "2024년 성희롱 예방 교육"                      |
| fileUrl         | 재생 URL                          | string       | optional | true     | "https://cdn.example.com/video1.mp4"           |
| duration        | 길이(초)                          | number       | required | false    | 1800                                           |
| version         | 버전                              | number       | required | false    | 1                                              |
| departmentScope | 수강 가능 부서 목록 (JSON string) | string       | optional | true     | "[\"HR\", \"ENGINEERING\"]" (JSON 배열 문자열) |

### Status

| status  | response content |
| ------- | ---------------- |
| 200 OK  | 정상             |
| 401/403 | 인증/권한 오류   |

---

# 2. Education (User)

## 2.1 사용자 자신 교육 및 영상 목록 조회

### ✔ URL

- GET /edus/me

### ✔ 설명

- 로그인 사용자 기준 이수해야 할 교육 목록과 각 교육에 포함된 영상 목록을 반환합니다.
- 각 교육 객체에 사용자 기준 진행률(progressPercent)과 시청 상태(watchStatus)가 포함되며,
  교육별 `videos` 배열로 영상별 시청률(progressPercent), 시청 상태(watchStatus), 재생 URL(fileUrl) 등을 제공합니다.

### ✔ 권한

`ROLE_USER`

### ✔ 요청

Body: 없음

### Query Parameter

| key       | 설명                      | value 타입 | 옵션     | Nullable | 예시        |
| --------- | ------------------------- | ---------- | -------- | -------- | ----------- |
| completed | 이수 여부                 | boolean    | optional | true     | true        |
| eduType   | 교육 유형 필터            | string     | optional | true     | "MANDATORY" |
| sort      | 정렬 기준(UPDATED\|TITLE) | string     | optional | true     | "UPDATED"   |

### Response

array of object

| key             | 설명                                   | value 타입    | 옵션     | Nullable | 예시                                   |
| --------------- | -------------------------------------- | ------------- | -------- | -------- | -------------------------------------- |
| id              | 교육 ID                                | string(uuid)  | required | false    | "550e8400-e29b-41d4-a716-446655440000" |
| title           | 제목                                   | string        | required | false    | "산업안전 교육"                        |
| description     | 설명                                   | string        | optional | true     | "산업안전 수칙"                        |
| category        | 주제 카테고리                          | string        | required | false    | "JOB_DUTY"                             |
| eduType         | 교육 유형                              | string        | optional | true     | "MANDATORY"                            |
| required        | 필수 여부                              | boolean       | required | false    | true                                   |
| progressPercent | 사용자 기준 교육 진행률(%)             | number        | required | false    | 60                                     |
| watchStatus     | 교육 시청 상태(시청전/시청중/시청완료) | string        | required | false    | "시청중"                               |
| videos          | 교육에 포함된 영상 목록(PUBLISHED만)   | array(object) | required | false    | 아래 표 참조                           |

videos item

| key               | 설명                              | value 타입   | 옵션     | Nullable | 예시                                           |
| ----------------- | --------------------------------- | ------------ | -------- | -------- | ---------------------------------------------- |
| id                | 영상 ID                           | string(uuid) | required | false    | "550e8400-e29b-41d4-a716-446655440001"         |
| title             | 영상 제목                         | string       | required | false    | "2024년 성희롱 예방 교육"                      |
| fileUrl           | 영상 파일 URL                     | string       | required | false    | "https://cdn.example.com/video1.mp4"           |
| duration          | 영상 길이(초)                     | number       | required | false    | 1800                                           |
| version           | 영상 버전                         | number       | required | false    | 1                                              |
| departmentScope   | 수강 가능 부서 목록 (JSON string) | string       | optional | true     | "[\"HR\", \"ENGINEERING\"]" (JSON 배열 문자열) |
| resumePosition    | 사용자 이어보기 위치(초)          | number       | optional | true     | 120                                            |
| isCompleted       | 사용자 영상 이수 여부             | boolean      | required | false    | false                                          |
| totalWatchSeconds | 사용자 누적 시청 시간(초)         | number       | optional | true     | 300                                            |
| progressPercent   | 진행률(%)                         | number       | required | false    | 65                                             |
| watchStatus       | 시청 상태(시청전/시청중/시청완료) | string       | required | false    | "시청중"                                       |

### Status

| status  | response content |
| ------- | ---------------- |
| 200 OK  | 정상             |
| 401/403 | 인증/권한 오류   |

---

## 2.2 교육 영상 목록 조회

### ✔ URL

- GET /edu/{id}/videos

### ✔ 설명

- 교육에 포함된 영상 목록과 사용자별 진행 정보를 조회합니다.
- **PUBLISHED 상태의 영상만** 반환됩니다.

### ✔ 권한

`ROLE_USER`

### ✔ 요청

Body: 없음

### Path

- id: UUID (교육 ID)

### Response

| key    | 설명      | value 타입    | 옵션     | Nullable | 예시                                   |
| ------ | --------- | ------------- | -------- | -------- | -------------------------------------- |
| id     | 교육 ID   | string(uuid)  | required | false    | "550e8400-e29b-41d4-a716-446655440000" |
| title  | 교육 제목 | string        | required | false    | "산업안전 교육"                        |
| videos | 영상 목록 | array(object) | required | false    | 아래 표 참조                           |

videos item

| key               | 설명                              | value 타입   | 옵션     | Nullable | 예시                                           |
| ----------------- | --------------------------------- | ------------ | -------- | -------- | ---------------------------------------------- |
| id                | 영상 ID                           | string(uuid) | required | false    | "550e8400-e29b-41d4-a716-446655440001"         |
| title             | 영상 제목                         | string       | required | false    | "2024년 성희롱 예방 교육"                      |
| fileUrl           | 재생 URL                          | string       | optional | true     | "https://cdn.example.com/video1.mp4"           |
| duration          | 길이(초)                          | number       | required | false    | 1800                                           |
| version           | 버전                              | number       | required | false    | 1                                              |
| departmentScope   | 수강 가능 부서 목록 (JSON string) | string       | optional | true     | "[\"HR\", \"ENGINEERING\"]" (JSON 배열 문자열) |
| resumePosition    | 이어보기 위치                     | number       | optional | true     | 600                                            |
| isCompleted       | 사용자 영상 이수                  | boolean      | optional | true     | false                                          |
| totalWatchSeconds | 누적 시청시간                     | number       | optional | true     | 600                                            |
| progressPercent   | 진행률(%)                         | number       | optional | true     | 33                                             |
| watchStatus       | 시청 상태(시청전/시청중/시청완료) | string       | optional | true     | "시청중"                                       |

### Status

| status        | response content    |
| ------------- | ------------------- |
| 200 OK        | 정상                |
| 404 Not Found | 교육을 찾을 수 없음 |
| 401/403       | 인증/권한 오류      |

---

## 2.3 영상 시청 진행률 업데이트

### ✔ URL

- POST /edu/{educationId}/video/{videoId}/progress

### ✔ 설명

- 특정 교육의 특정 영상에 대한 사용자 시청 진행 정보를 업데이트합니다.
- 영상의 `duration`은 백엔드에서 `EducationVideo` 엔티티에서 자동으로 가져옵니다.
- 영상 완료 여부는 교육의 `passRatio` 기준으로 판단됩니다 (기본값: 100%).
- 교육 전체 진행률은 **PUBLISHED 상태의 영상만** 기준으로 계산됩니다.
- **교육의 모든 PUBLISHED 영상이 시청 완료되면 자동으로 교육 시청 완료 처리됩니다.** (`EducationProgress` 엔티티가 자동으로 업데이트됨)

### ✔ 권한

`ROLE_USER`

### ✔ 요청

Body: 있음

### Path

- educationId: UUID (교육 ID)
- videoId: UUID (영상 ID)

### Request

| key       | 설명              | value 타입 | 옵션     | Nullable | 예시 |
| --------- | ----------------- | ---------- | -------- | -------- | ---- |
| position  | 현재 위치(초)     | number     | optional | true     | 120  |
| watchTime | 증가 시청시간(초) | number     | optional | true     | 120  |

**참고**: `duration` 필드는 제거되었습니다. 백엔드에서 `EducationVideo` 엔티티의 `duration` 값을 사용합니다.

### Response

| key               | 설명                | value 타입 | 옵션     | Nullable | 예시  |
| ----------------- | ------------------- | ---------- | -------- | -------- | ----- |
| updated           | 처리 여부           | boolean    | required | false    | true  |
| progress          | 현재 영상 진행률(%) | number     | required | false    | 7     |
| isCompleted       | 영상 이수 여부      | boolean    | required | false    | false |
| totalWatchSeconds | 누적 시청 시간      | number     | required | false    | 120   |
| eduProgress       | 교육 전체 진행률(%) | number     | required | false    | 40    |
| eduCompleted      | 교육 이수 여부      | boolean    | required | false    | false |

### Status

| status          | response content      |
| --------------- | --------------------- |
| 200 OK          | 정상                  |
| 400 Bad Request | 유효성 실패           |
| 404 Not Found   | 리소스를 찾을 수 없음 |
| 401/403         | 인증/권한 오류        |

---

## 2.4 교육 이수 처리 (선택적)

### ✔ URL

- POST /edu/{id}/complete

### ✔ 설명

- 모든 영상 이수 여부를 확인하여 교육 이수 처리를 수행합니다.
- **PUBLISHED 상태의 영상만** 기준으로 이수 여부를 판단합니다.
- 모든 PUBLISHED 영상이 완료 상태여야 교육 이수가 완료됩니다.
- **참고**: `POST /edu/{educationId}/video/{videoId}/progress` API에서 모든 영상이 완료되면 자동으로 교육 이수가 처리되므로, 이 API는 필요시에만 수동으로 호출하면 됩니다.

### ✔ 권한

`ROLE_USER`

### ✔ 요청

Body: 없음

### Path

- id: UUID (교육 ID)

### Response

성공 시 (200 OK)

| key         | 설명      | value 타입       | 옵션     | Nullable | 예시                   |
| ----------- | --------- | ---------------- | -------- | -------- | ---------------------- |
| status      | 상태      | string           | required | false    | "COMPLETED"            |
| completedAt | 이수 시각 | string(ISO-8601) | required | false    | "2025-12-17T10:00:00Z" |

실패 시 (400 Bad Request)

| key     | 설명        | value 타입 | 옵션     | Nullable | 예시                                     |
| ------- | ----------- | ---------- | -------- | -------- | ---------------------------------------- |
| status  | 상태        | string     | required | false    | "FAILED"                                 |
| message | 실패 메시지 | string     | required | false    | "PUBLISHED 상태의 영상 이수 조건 미충족" |

### Status

| status          | response content                  |
| --------------- | --------------------------------- |
| 200 OK          | 이수 완료                         |
| 400 Bad Request | 이수 조건 미충족 또는 잘못된 요청 |
| 401/403         | 인증/권한 오류                    |

---

## 주의사항

1. **교육 카테고리 (category)**: 주제 카테고리는 다음 중 하나여야 합니다:
   - `JOB_DUTY` (직무)
   - `SEXUAL_HARASSMENT_PREVENTION` (성희롱 예방)
   - `PERSONAL_INFO_PROTECTION` (개인정보 보호)
   - `WORKPLACE_BULLYING` (직장 내 괴롭힘)
   - `DISABILITY_AWARENESS` (장애인 인식 개선)
2. **교육 유형 (eduType)**: 교육 유형은 다음 중 하나입니다:
   - `MANDATORY` (법정 필수)
   - `JOB` (직무)
   - `ETC` (기타)
3. **진행률 계산**:
   - 영상 진행률: `(position / duration) * 100` (최소 0, 최대 100)
   - 교육 진행률: PUBLISHED 영상들의 진행률 평균
4. **완료 기준**:
   - 영상 완료: `progress >= passRatio` (교육의 `passRatio` 기준, 기본값: 100%)
   - 교육 이수: 모든 **PUBLISHED 상태의 영상**이 완료 상태여야 함
5. **영상 상태 필터링**:
   - 사용자 API (`GET /edus/me`, `GET /edu/{id}/videos`)는 **PUBLISHED 상태의 영상만** 반환
   - 진행률 업데이트 및 이수 처리도 **PUBLISHED 영상만** 기준으로 계산

---

# 3. Dashboard Statistics (Admin)

## 3.1 대시보드 요약 통계 조회

### ✔ URL

- GET /admin/dashboard/education/summary

### ✔ 설명

- 전체 평균 이수율, 미이수자 수, 4대 의무교육 평균, 직무교육 평균을 조회합니다.
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

| key                           | 설명                    | value 타입 | 옵션     | Nullable | 예시  |
| ----------------------------- | ----------------------- | ---------- | -------- | -------- | ----- |
| overallAverageCompletionRate  | 전체 평균 이수율(%)     | number     | required | false    | 85.5   |
| nonCompleterCount             | 미이수자 수             | number     | required | false    | 15     |
| mandatoryEducationAverage      | 4대 의무교육 평균 이수율(%) | number     | required | false    | 90.2   |
| jobEducationAverage           | 직무교육 평균 이수율(%) | number     | required | false    | 78.3   |

### Status

| status  | response content |
| ------- | ---------------- |
| 200 OK  | 정상             |
| 401/403 | 인증/권한 오류   |

---

## 3.2 4대 의무교육 이수율 조회

### ✔ URL

- GET /admin/dashboard/education/mandatory-completion

### ✔ 설명

- 성희롱 예방교육, 개인정보보호 교육, 직장 내 괴롭힘 예방, 장애인 인식개선 교육의 이수율을 조회합니다.
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

| key                        | 설명                      | value 타입 | 옵션     | Nullable | 예시  |
| -------------------------- | ------------------------- | ---------- | -------- | -------- | ----- |
| sexualHarassmentPrevention | 성희롱 예방교육 이수율(%) | number     | required | false    | 95.0   |
| personalInfoProtection     | 개인정보보호 교육 이수율(%) | number     | required | false    | 92.5   |
| workplaceBullying          | 직장 내 괴롭힘 예방 이수율(%) | number     | required | false    | 88.3   |
| disabilityAwareness        | 장애인 인식개선 이수율(%) | number     | required | false    | 90.1   |

### Status

| status  | response content |
| ------- | ---------------- |
| 200 OK  | 정상             |
| 401/403 | 인증/권한 오류   |

---

## 3.3 직무교육 이수 현황 조회

### ✔ URL

- GET /admin/dashboard/education/job-completion

### ✔ 설명

- 직무교육별 상태(진행 중/이수 완료)와 학습자 수를 조회합니다.
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

array of object

| key          | 설명                      | value 타입   | 옵션     | Nullable | 예시                                   |
| ------------ | ------------------------- | ------------ | -------- | -------- | -------------------------------------- |
| educationId  | 교육 ID                   | string(uuid) | required | false    | "550e8400-e29b-41d4-a716-446655440000" |
| title        | 교육 제목                 | string       | required | false    | "신입사원 온보딩 교육"                 |
| status       | 상태 (진행 중/이수 완료)  | string       | required | false    | "진행 중"                              |
| learnerCount | 학습자 수                 | number       | required | false    | 25                                     |

### Status

| status  | response content |
| ------- | ---------------- |
| 200 OK  | 정상             |
| 401/403 | 인증/권한 오류   |

---

## 3.4 부서별 이수율 현황 조회

### ✔ URL

- GET /admin/dashboard/education/department-completion

### ✔ 설명

- 부서별 대상자 수, 이수자 수, 이수율, 미이수자 수를 조회합니다.
- 이수율 기준으로 내림차순 정렬됩니다.
- 기간 필터(period)를 지원합니다.

### ✔ 권한

`ROLE_ADMIN`

### ✔ 요청

Body: 없음

### Query Parameter

| key    | 설명                    | value 타입 | 옵션     | Nullable | 예시 |
| ------ | ----------------------- | ---------- | -------- | -------- | ---- |
| period | 기간 (일수, 7/30/90)    | number     | optional | true     | 30   |

### Response

array of object

| key             | 설명          | value 타입 | 옵션     | Nullable | 예시   |
| --------------- | ------------- | ---------- | -------- | -------- | ------ |
| department      | 부서명        | string     | required | false    | "총무팀" |
| targetCount     | 대상자 수     | number     | required | false    | 50     |
| completerCount  | 이수자 수     | number     | required | false    | 45     |
| completionRate  | 이수율(%)     | number     | required | false    | 90.0   |
| nonCompleterCount | 미이수자 수 | number     | required | false    | 5      |

### Status

| status  | response content |
| ------- | ---------------- |
| 200 OK  | 정상             |
| 401/403 | 인증/권한 오류   |

---

## 관련 문서

- [Video API 명세](../video/api/video_api_spec.md)
- [Quiz API 명세](../quiz/api/quiz_api_spec.md)
