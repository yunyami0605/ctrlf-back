# 관리자 대시보드 퀴즈 페이지 플로우

관리자가 퀴즈 성적 현황을 모니터링하고 통계를 조회하는 페이지의 API 플로우입니다.

## 1. 페이지 진입 및 초기 데이터 조회

페이지 진입 시 다음 API들을 호출하여 초기 데이터를 조회합니다.

```
1. GET /admin/dashboard/quiz/summary?period=30
   → 대시보드 요약 통계 조회 (기본값: 최근 30일)
   → Response: {
       overallAverageScore: 84.0,      // 전체 평균 점수
       participantCount: 176,          // 응시자 수
       passRate: 78.0,                 // 통과율 (80점↑) (%)
       participationRate: 73.0        // 퀴즈 응시율 (%)
     }

2. GET /admin/dashboard/quiz/department-scores?period=30
   → 부서별 평균 점수 조회
   → Response: {
       items: [{
         department: "인사팀",
         averageScore: 89.0,           // 평균 점수
         participantCount: 13          // 응시자 수
       }, {
         department: "법무팀",
         averageScore: 88.0,
         participantCount: 10
       }, {
         department: "기획팀",
         averageScore: 87.0,
         participantCount: 19
       }, {
         department: "재무팀",
         averageScore: 86.0,
         participantCount: 14
       }, {
         department: "개발팀",
         averageScore: 85.0,
         participantCount: 28
       }, {
         department: "총무팀",
         averageScore: 83.0,
         participantCount: 15
       }, {
         department: "마케팅팀",
         averageScore: 81.0,
         participantCount: 16
       }, {
         department: "영업팀",
         averageScore: 79.0,
         participantCount: 23
       }]
     }

3. GET /admin/dashboard/quiz/quiz-stats?period=30
   → 퀴즈별 통계 조회
   → Response: {
       items: [{
         educationId: "교육 ID",
         quizTitle: "성희롱 예방교육 퀴즈",
         attemptNo: 2,                 // 회차
         averageScore: 88.0,           // 평균 점수
         attemptCount: 26,             // 응시 수
         passRate: 85.0                // 통과율 (%)
       }, {
         educationId: "교육 ID",
         quizTitle: "개인정보보호 퀴즈",
         attemptNo: 1,
         averageScore: 86.0,
         attemptCount: 57,
         passRate: 81.0
       }, {
         educationId: "교육 ID",
         quizTitle: "직장 내 괴롭힘 예방 퀴즈",
         attemptNo: 1,
         averageScore: 83.0,
         attemptCount: 49,
         passRate: 75.0
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
1. GET /admin/dashboard/quiz/summary?period=7
2. GET /admin/dashboard/quiz/department-scores?period=7
3. GET /admin/dashboard/quiz/quiz-stats?period=7
```

**예시: 최근 90일로 변경**

```
1. GET /admin/dashboard/quiz/summary?period=90
2. GET /admin/dashboard/quiz/department-scores?period=90
3. GET /admin/dashboard/quiz/quiz-stats?period=90
```

## 3. 부서 필터 변경

사용자가 부서 드롭다운에서 특정 부서를 선택하면, 해당 부서 기준으로 통계를 조회합니다.

**부서 필터 적용 API:**
- `GET /admin/dashboard/quiz/summary?period=30&department=총무팀`
- `GET /admin/dashboard/quiz/department-scores?period=30&department=총무팀`
- `GET /admin/dashboard/quiz/quiz-stats?period=30&department=총무팀`

**예시: 총무팀 필터 적용**

```
1. GET /admin/dashboard/quiz/summary?period=30&department=총무팀
   → 총무팀 기준 전체 평균 점수, 응시자 수, 통과율, 응시율

2. GET /admin/dashboard/quiz/department-scores?period=30&department=총무팀
   → 총무팀만 조회 (단일 부서이므로 1개 항목만 반환)

3. GET /admin/dashboard/quiz/quiz-stats?period=30&department=총무팀
   → 총무팀 기준 퀴즈별 통계
```

**예시: 전체 부서로 변경 (필터 해제)**

```
1. GET /admin/dashboard/quiz/summary?period=30
   → department 파라미터 제거

2. GET /admin/dashboard/quiz/department-scores?period=30
   → department 파라미터 제거

3. GET /admin/dashboard/quiz/quiz-stats?period=30
   → department 파라미터 제거
```

## 4. 데이터 새로고침

사용자가 "데이터 새로고침" 버튼을 클릭하면 현재 선택된 필터(기간, 부서) 기준으로 모든 API를 다시 호출합니다.

```
현재 필터 상태:
- 기간: 최근 30일 (period=30)
- 부서: 총무팀 (department=총무팀)

새로고침 시 호출:
1. GET /admin/dashboard/quiz/summary?period=30&department=총무팀
2. GET /admin/dashboard/quiz/department-scores?period=30&department=총무팀
3. GET /admin/dashboard/quiz/quiz-stats?period=30&department=총무팀
```

## 5. 플로우 다이어그램

```
[페이지 진입]
    ↓
[초기 데이터 조회]
    ├─ GET /admin/dashboard/quiz/summary?period=30
    ├─ GET /admin/dashboard/quiz/department-scores?period=30
    └─ GET /admin/dashboard/quiz/quiz-stats?period=30
    ↓
[화면 렌더링]
    ├─ 4개 통계 카드 표시
    ├─ 부서별 평균 점수 바 차트 표시
    └─ 퀴즈별 통계 테이블 표시
    ↓
[사용자 액션]
    ├─ [기간 필터 변경]
    │   └─ 모든 API에 period 파라미터 변경하여 재호출
    │
    ├─ [부서 필터 변경]
    │   └─ 모든 API에 department 파라미터 추가/제거
    │
    └─ [데이터 새로고침]
        └─ 현재 필터 상태 기준으로 모든 API 재호출
```

## 6. 화면 구성 요소

### 6.1 통계 카드 (4개)

1. **전체 평균 점수**
   - API: `GET /admin/dashboard/quiz/summary`
   - 필드: `overallAverageScore`
   - 표시 형식: `84점` (소수점 첫째자리까지)

2. **응시자 수**
   - API: `GET /admin/dashboard/quiz/summary`
   - 필드: `participantCount`
   - 표시 형식: `176명`

3. **통과율 (80점↑)**
   - API: `GET /admin/dashboard/quiz/summary`
   - 필드: `passRate`
   - 표시 형식: `78%`

4. **퀴즈 응시율**
   - API: `GET /admin/dashboard/quiz/summary`
   - 필드: `participationRate`
   - 표시 형식: `73%`

### 6.2 부서별 평균 점수 섹션

- API: `GET /admin/dashboard/quiz/department-scores`
- 바 차트 형식으로 표시:
  - 각 부서별 평균 점수를 바 길이로 표현
  - 부서명과 평균 점수, 응시자 수 표시
  - 예: "인사팀: 89점 / 13명"
- 정렬: 평균 점수 기준 내림차순 (높은 순서대로)

### 6.3 퀴즈별 통계 테이블

- API: `GET /admin/dashboard/quiz/quiz-stats`
- 테이블 컬럼:
  - 퀴즈 제목: `quizTitle`
  - 회차: `attemptNo`
  - 평균 점수: `averageScore` (점)
  - 응시 수: `attemptCount` (명)
  - 통과율: `passRate` (%)
- 정렬: 평균 점수 기준 내림차순 (높은 순서대로)

## 7. 필터 조합 예시

### 예시 1: 최근 7일 + 총무팀

```
1. GET /admin/dashboard/quiz/summary?period=7&department=총무팀
2. GET /admin/dashboard/quiz/department-scores?period=7&department=총무팀
3. GET /admin/dashboard/quiz/quiz-stats?period=7&department=총무팀
```

### 예시 2: 최근 90일 + 전체 부서

```
1. GET /admin/dashboard/quiz/summary?period=90
2. GET /admin/dashboard/quiz/department-scores?period=90
3. GET /admin/dashboard/quiz/quiz-stats?period=90
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

페이지 진입 시 3개 API를 병렬로 호출하여 초기 로딩 시간을 단축합니다.

```
Promise.all([
  fetch('/admin/dashboard/quiz/summary?period=30'),
  fetch('/admin/dashboard/quiz/department-scores?period=30'),
  fetch('/admin/dashboard/quiz/quiz-stats?period=30')
])
```

### 9.2 캐싱

- 동일한 필터 조합의 경우 일정 시간(예: 5분) 동안 캐시된 데이터 사용
- 새로고침 버튼 클릭 시 캐시 무시하고 최신 데이터 조회

## 10. 통계 계산 기준

### 10.1 통과율 계산

- 통과 기준: 80점 이상
- 계산식: `(80점 이상 시도 수 / 전체 시도 수) * 100`

### 10.2 응시율 계산

- 현재는 전체 사용자 수 조회가 필요합니다.
- TODO: infra-service에서 전체 사용자 수를 조회하는 API 연동 필요
- 계산식: `(응시자 수 / 전체 사용자 수) * 100`

### 10.3 평균 점수 계산

- 각 시도의 점수를 평균하여 계산
- 부서별/퀴즈별 평균은 해당 그룹의 모든 시도 점수의 평균

## 11. 관련 API 명세

- [Quiz API 명세](../api/quiz_api_spec.md) - 섹션 2. Quiz Dashboard Statistics (Admin)

