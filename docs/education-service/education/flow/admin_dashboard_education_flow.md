# 관리자 대시보드 교육 페이지 플로우

관리자가 교육 이수 현황을 모니터링하고 통계를 조회하는 페이지의 API 플로우입니다.

## 1. 페이지 진입 및 초기 데이터 조회

페이지 진입 시 다음 API들을 호출하여 초기 데이터를 조회합니다.

```
1. GET /admin/dashboard/education/summary?period=30
   → 대시보드 요약 통계 조회 (기본값: 최근 30일)
   → Response: {
       overallAverageCompletionRate: 87.0,      // 전체 평균 이수율 (%)
       nonCompleterCount: 42,                   // 미이수자 수
       mandatoryEducationAverage: 91.0,         // 4대 의무교육 평균 이수율 (%)
       jobEducationAverage: 82.0               // 직무교육 평균 이수율 (%)
     }

2. GET /admin/dashboard/education/mandatory-completion?period=30
   → 4대 의무교육 이수율 조회
   → Response: {
       sexualHarassmentPrevention: 95.0,        // 성희롱 예방교육 이수율 (%)
       personalInfoProtection: 92.0,           // 개인정보보호 교육 이수율 (%)
       workplaceBullying: 88.0,                 // 직장 내 괴롭힘 예방 이수율 (%)
       disabilityAwareness: 89.0                // 장애인 인식개선 이수율 (%)
     }

3. GET /admin/dashboard/education/job-completion?period=30
   → 직무교육 이수 현황 조회
   → Response: {
       items: [{
         educationId: "교육 ID",
         title: "보안 관제 기초",
         status: "진행 중" | "이수 완료",
         learnerCount: 38
       }, {
         educationId: "교육 ID",
         title: "AI 기반 위협 탐지",
         status: "이수 완료",
         learnerCount: 21
       }, {
         educationId: "교육 ID",
         title: "신규 입사자 온보딩(전사 공통)",
         status: "이수 완료",
         learnerCount: 47
       }]
     }

4. GET /admin/dashboard/education/department-completion?period=30
   → 부서별 이수율 현황 조회
   → Response: {
       items: [{
         department: "총무팀",
         targetCount: 18,                       // 대상자 수
         completerCount: 17,                    // 이수자 수
         completionRate: 94.0,                  // 이수율 (%)
         nonCompleterCount: 1                   // 미이수자 수
       }, {
         department: "기획팀",
         targetCount: 21,
         completerCount: 19,
         completionRate: 90.0,
         nonCompleterCount: 2
       }, {
         department: "마케팅팀",
         targetCount: 16,
         completerCount: 13,
         completionRate: 81.0,
         nonCompleterCount: 3
       }, {
         department: "인사팀",
         targetCount: 14,
         completerCount: 13,
         completionRate: 93.0,
         nonCompleterCount: 1
       }, {
         department: "재무팀",
         targetCount: 15,
         completerCount: 13,
         completionRate: 87.0,
         nonCompleterCount: 2
       }]
     }
```

**참고:**

- 모든 API는 기본적으로 `period=30` (최근 30일)을 사용합니다.
- 부서 필터는 기본적으로 적용되지 않습니다 (전체 부서 기준).

## 2. 기간 필터 변경

사용자가 기간 필터 버튼을 클릭하면 해당 기간에 맞춰 모든 API를 다시 호출합니다.

```
기간 옵션:
- 최근 7일: period=7
- 최근 30일: period=30 (기본값)
- 최근 90일: period=90
```

**예시: 최근 7일로 변경**

```
1. GET /admin/dashboard/education/summary?period=7
2. GET /admin/dashboard/education/mandatory-completion?period=7
3. GET /admin/dashboard/education/job-completion?period=7
4. GET /admin/dashboard/education/department-completion?period=7
```

**예시: 최근 90일로 변경**

```
1. GET /admin/dashboard/education/summary?period=90
2. GET /admin/dashboard/education/mandatory-completion?period=90
3. GET /admin/dashboard/education/job-completion?period=90
4. GET /admin/dashboard/education/department-completion?period=90
```

## 3. 부서 필터 변경

사용자가 부서 드롭다운에서 특정 부서를 선택하면, 해당 부서 기준으로 통계를 조회합니다.

**부서 필터 적용 API:**

- `GET /admin/dashboard/education/summary?period=30&department=총무팀`
- `GET /admin/dashboard/education/mandatory-completion?period=30&department=총무팀`
- `GET /admin/dashboard/education/job-completion?period=30&department=총무팀`

**부서 필터 미적용 API:**

- `GET /admin/dashboard/education/department-completion?period=30` (부서별 통계이므로 부서 필터 없음)

**예시: 총무팀 필터 적용**

```
1. GET /admin/dashboard/education/summary?period=30&department=총무팀
   → 총무팀 기준 전체 평균 이수율, 미이수자 수, 4대 의무교육 평균, 직무교육 평균

2. GET /admin/dashboard/education/mandatory-completion?period=30&department=총무팀
   → 총무팀 기준 4대 의무교육 이수율

3. GET /admin/dashboard/education/job-completion?period=30&department=총무팀
   → 총무팀 기준 직무교육 이수 현황

4. GET /admin/dashboard/education/department-completion?period=30
   → 부서별 통계는 그대로 조회 (부서 필터 미적용)
```

**예시: 전체 부서로 변경 (필터 해제)**

```
1. GET /admin/dashboard/education/summary?period=30
   → department 파라미터 제거

2. GET /admin/dashboard/education/mandatory-completion?period=30
   → department 파라미터 제거

3. GET /admin/dashboard/education/job-completion?period=30
   → department 파라미터 제거

4. GET /admin/dashboard/education/department-completion?period=30
   → 부서별 통계는 그대로 조회
```

## 4. 데이터 새로고침

사용자가 "데이터 새로고침" 버튼을 클릭하면 현재 선택된 필터(기간, 부서) 기준으로 모든 API를 다시 호출합니다.

```
현재 필터 상태:
- 기간: 최근 30일 (period=30)
- 부서: 총무팀 (department=총무팀)

새로고침 시 호출:
1. GET /admin/dashboard/education/summary?period=30&department=총무팀
2. GET /admin/dashboard/education/mandatory-completion?period=30&department=총무팀
3. GET /admin/dashboard/education/job-completion?period=30&department=총무팀
4. GET /admin/dashboard/education/department-completion?period=30
```

## 5. 플로우 다이어그램

```
[페이지 진입]
    ↓
[초기 데이터 조회]
    ├─ GET /admin/dashboard/education/summary?period=30
    ├─ GET /admin/dashboard/education/mandatory-completion?period=30
    ├─ GET /admin/dashboard/education/job-completion?period=30
    └─ GET /admin/dashboard/education/department-completion?period=30
    ↓
[화면 렌더링]
    ├─ 4개 통계 카드 표시
    ├─ 4대 의무교육 이수율 섹션 표시
    ├─ 직무교육 이수 현황 섹션 표시
    └─ 부서별 이수율 현황 테이블 표시
    ↓
[사용자 액션]
    ├─ [기간 필터 변경]
    │   └─ 모든 API에 period 파라미터 변경하여 재호출
    │
    ├─ [부서 필터 변경]
    │   └─ summary, mandatory-completion, job-completion API에 department 파라미터 추가/제거
    │
    └─ [데이터 새로고침]
        └─ 현재 필터 상태 기준으로 모든 API 재호출
```

## 6. 화면 구성 요소

### 6.1 통계 카드 (4개)

1. **전체 평균 이수율**

   - API: `GET /admin/dashboard/education/summary`
   - 필드: `overallAverageCompletionRate`
   - 표시 형식: `87%` (소수점 첫째자리까지)

2. **미이수자 수**

   - API: `GET /admin/dashboard/education/summary`
   - 필드: `nonCompleterCount`
   - 표시 형식: `42명`

3. **4대 의무교육 평균**

   - API: `GET /admin/dashboard/education/summary`
   - 필드: `mandatoryEducationAverage`
   - 표시 형식: `91%`

4. **직무교육 평균**
   - API: `GET /admin/dashboard/education/summary`
   - 필드: `jobEducationAverage`
   - 표시 형식: `82%`

### 6.2 4대 의무교육 이수율 섹션

- API: `GET /admin/dashboard/education/mandatory-completion`
- 각 항목별 진행 바와 이수율 표시:
  - 성희롱 예방교육: `sexualHarassmentPrevention` (예: 95%)
  - 개인정보보호 교육: `personalInfoProtection` (예: 92%)
  - 직장 내 괴롭힘 예방: `workplaceBullying` (예: 88%)
  - 장애인 인식개선: `disabilityAwareness` (예: 89%)

### 6.3 직무교육 이수 현황 섹션

- API: `GET /admin/dashboard/education/job-completion`
- 각 교육별:
  - 제목: `title`
  - 상태: `status` ("진행 중" 또는 "이수 완료")
  - 학습자 수: `learnerCount` (예: "학습자 38명")

### 6.4 부서별 이수율 현황 테이블

- API: `GET /admin/dashboard/education/department-completion`
- 테이블 컬럼:
  - 부서: `department`
  - 대상자 수: `targetCount`
  - 이수자 수: `completerCount`
  - 이수율: `completionRate` (%)
  - 미이수자 수: `nonCompleterCount`
- 정렬: 이수율 기준 내림차순 (높은 순서대로)

## 7. 필터 조합 예시

### 예시 1: 최근 7일 + 총무팀

```
1. GET /admin/dashboard/education/summary?period=7&department=총무팀
2. GET /admin/dashboard/education/mandatory-completion?period=7&department=총무팀
3. GET /admin/dashboard/education/job-completion?period=7&department=총무팀
4. GET /admin/dashboard/education/department-completion?period=7
```

### 예시 2: 최근 90일 + 전체 부서

```
1. GET /admin/dashboard/education/summary?period=90
2. GET /admin/dashboard/education/mandatory-completion?period=90
3. GET /admin/dashboard/education/job-completion?period=90
4. GET /admin/dashboard/education/department-completion?period=90
```

## 8. 에러 처리

### 8.1 API 호출 실패

- 네트워크 오류 또는 서버 오류 시:
  - 사용자에게 에러 메시지 표시
  - 재시도 버튼 제공
  - 이전 데이터 유지 (가능한 경우)

### 8.2 데이터 없음

- 특정 기간/부서에 데이터가 없는 경우:
  - 빈 상태 메시지 표시
  - 통계 값은 0 또는 null로 표시

## 9. 성능 최적화

### 9.1 병렬 호출

페이지 진입 시 4개 API를 병렬로 호출하여 초기 로딩 시간을 단축합니다.

```
Promise.all([
  fetch('/admin/dashboard/education/summary?period=30'),
  fetch('/admin/dashboard/education/mandatory-completion?period=30'),
  fetch('/admin/dashboard/education/job-completion?period=30'),
  fetch('/admin/dashboard/education/department-completion?period=30')
])
```

### 9.2 캐싱

- 동일한 필터 조합의 경우 일정 시간(예: 5분) 동안 캐시된 데이터 사용
- 새로고침 버튼 클릭 시 캐시 무시하고 최신 데이터 조회

## 10. 관련 API 명세

- [Education API 명세](../api/education_api_spec.md) - 섹션 3. Dashboard Statistics (Admin)
