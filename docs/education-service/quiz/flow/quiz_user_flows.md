# 퀴즈 유저 플로우 분석

## API 목록

1. `GET /quiz/available-educations` - 풀 수 있는 퀴즈 목록 조회
2. `GET /quiz/{eduId}/start` - 퀴즈 시작(문항 생성/복원)
3. `GET /quiz/attempt/{attemptId}/timer` - 타이머 정보 조회
4. `POST /quiz/attempt/{attemptId}/save` - 응답 임시 저장
5. `POST /quiz/attempt/{attemptId}/leave` - 퀴즈 이탈 기록
6. `POST /quiz/attempt/{attemptId}/submit` - 퀴즈 제출/채점
7. `GET /quiz/attempt/{attemptId}/result` - 퀴즈 결과 조회
8. `GET /quiz/{attemptId}/wrongs` - 오답노트 목록 조회
9. `GET /quiz/my-attempts` - 내가 풀었던 퀴즈 응시 내역 조회
10. `GET /quiz/{eduId}/retry-info` - 퀴즈 재응시 정보 조회
11. `GET /quiz/department-stats` - 부서별 퀴즈 통계 조회

---

## 주요 유저 플로우

### 플로우 0: 퀴즈 초기 페이지 진입

```
1. GET /quiz/department-stats
   → 부서별 퀴즈 통계 조회
   → 응답: 부서별 점수판 정보 (departmentName, averageScore, progressPercent, participantCount)
   → 예시: [
       { departmentName: "인사팀", averageScore: 85, progressPercent: 50, participantCount: 20 },
       { departmentName: "총무팀", averageScore: 85, progressPercent: 50, participantCount: 15 },
       { departmentName: "기획팀", averageScore: 85, progressPercent: 50, participantCount: 10 }
     ]

2. GET /quiz/available-educations
   → 풀 수 있는 퀴즈 목록 조회 (이수 완료한 교육만)
   → 응답: 교육 목록 (educationId, title, category, eduType, attemptCount, maxAttempts, hasAttempted, bestScore, passed)
   → 예시: [
       {
         educationId: "...",
         title: "직장 내 성희롱 예방",
         category: "SEXUAL_HARASSMENT_PREVENTION",
         eduType: "MANDATORY",
         attemptCount: 0,
         maxAttempts: 2,   // 최대 응시 횟수
         hasAttempted: false, // 이미 응시 여부
         bestScore: null,  // 미응시 시 null → 프론트에서 "-"로 표시
         passed: null
       },
       {
         educationId: "...",
         title: "개인정보 보호",
         category: "PERSONAL_INFO_PROTECTION",
         eduType: "MANDATORY",
         attemptCount: 0,
         maxAttempts: null,
         hasAttempted: false,
         bestScore: null,  // 미응시 시 null → 프론트에서 "-"로 표시
         passed: null
       }
     ]

3. [UI 표시]
   → 부서별 점수판: 부서명, 점수, 진행률 바 표시
   → 퀴즈 목록: 교육 제목, 개인 점수 (bestScore가 null이면 "-" 표시)
   → "교육 영상 시청 완료 후 퀴즈를 풀 수 있어요" 메시지 (bestScore가 null이고 hasAttempted가 false인 경우)
```

**참고:**

- 두 API는 병렬로 호출 가능합니다.
- `bestScore`가 `null`이면 프론트엔드에서 "-"로 표시합니다.
- `hasAttempted: false`이고 `bestScore: null`이면 "교육 영상 시청 완료 후 퀴즈를 풀 수 있어요" 메시지를 표시합니다.
- `department-stats` API는 `educationId` 파라미터 없이 호출하면 전체 교육 대상 통계를 반환합니다.

---

### 플로우 1: 정상적인 퀴즈 응시 (첫 시도)

```
1. GET /quiz/available-educations
   → 풀 수 있는 퀴즈 목록 조회 (이수 완료한 교육)
   → 응답: 교육 목록 (educationId, title, category, eduType, attemptCount, maxAttempts, hasAttempted: false, bestScore, passed)

2. 사용자가 교육 선택

3. GET /quiz/{eduId}/start
   → 퀴즈 시작 (새 시도 생성)
   → AI 서버에서 문항 생성
   → 응답: attemptId, questions[] (questionId, order, question, choices, answerIndex)
   → **참고**: 모든 문항이 한 번에 반환됨 (페이지네이션은 프론트엔드에서 처리)

4. [퀴즈 풀이 페이지 진입]
   → UI 표시: 퀴즈 제목, 문항 목록 (페이지네이션 "1 2" 등), 선택지, 제출하기 버튼
   → 스크롤 가능한 영역에 모든 문항 표시

5. [반복] GET /quiz/attempt/{attemptId}/timer
   → 타이머 동기화 (주기적 호출, 예: 10초마다)
   → 응답: timeLimit, startedAt, expiresAt, remainingSeconds, isExpired
   → **참고**: 시간 만료 시 자동 제출 트리거

6. [반복] POST /quiz/attempt/{attemptId}/save
   → 답안 임시 저장 (사용자가 답 선택 시 또는 주기적으로)
   → Request: { answers: [{ questionId, userSelectedIndex }] }
   → 응답: saved, savedCount, savedAt
   → **참고**: 페이지 새로고침/이탈 시 복구용

7. [선택] POST /quiz/attempt/{attemptId}/leave
   → 이탈 기록 (탭 전환, 창 닫기 등 감지 시)
   → Request: { timestamp, reason, leaveSeconds }
   → 응답: recorded, leaveCount, lastLeaveAt

8. POST /quiz/attempt/{attemptId}/submit
   → 퀴즈 제출 및 채점 ("제출하기" 버튼 클릭 시)
   → Request: { answers: [{ questionId, userSelectedIndex }] }
   → 응답: score, passed, correctCount, wrongCount, totalCount, submittedAt

9. GET /quiz/attempt/{attemptId}/result
   → 결과 조회 (제출 후 자동 이동 또는 사용자 요청)
   → 응답: score, passed, passScore, correctCount, wrongCount, totalCount, finishedAt

10. [선택] GET /quiz/{attemptId}/wrongs
    → 오답노트 조회
    → 응답: 틀린 문항 목록 (question, userAnswerIndex, correctAnswerIndex, explanation, choices)
```

---

### 플로우 8: 오답노트 조회 및 해설 확인

```
1. (선행) 퀴즈 제출 및 결과 확인 완료
   → 사용자가 퀴즈 결과 화면에서 '오답노트' 버튼 클릭
   → 또는 마이페이지에서 과거 응시 내역 선택 후 '오답노트' 버튼 클릭

2. GET /quiz/{attemptId}/wrongs
   → 특정 퀴즈 시도에 대한 오답 문항 목록 조회
   → 응답: WrongNoteItem[] (question, userAnswerIndex, correctAnswerIndex, explanation, choices)
   → 예시: [
       {
         question: "직장 내 괴롭힘의 기본 개념으로 가장 적절한 것은 무엇인가요?",
         userAnswerIndex: 1,  // 사용자가 선택한 답 (틀림)
         correctAnswerIndex: 0,  // 정답
         explanation: "이 문항의 핵심은 \"업무상 적정 범위를 넘어 신체적·정신적 고통을 주는 행위\"가 설명하는 직장 내 괴롭힘의 기준입니다...",
         choices: [
           "직급에 따른 정당한 업무 지시",
           "업무상 적정 범위를 넘어 신체적·정신적 고통을 주는 행위",
           "업무 성과에 대한 객관적 평가",
           "정기적인 업무 보고 요구"
         ]
       },
       // ... 다른 오답 문항들
     ]

3. [오답노트 페이지 렌더링]
   → 조회된 오답 문항들을 순서대로 표시
   → 각 문항 옆에 '해설보기' 버튼 표시
   → 문제번호는 배열 인덱스 + 1로 표시 (또는 프론트엔드에서 관리)

4. [선택] '해설보기' 버튼 클릭
   → 해당 문항의 `explanation` 필드 내용을 모달/오버레이로 표시
   → "★정답 해설" 제목과 함께 해설 내용 표시
   → 별도 API 호출 없이 클라이언트에서 이미 받아온 데이터로 처리
   → 모달 닫기 버튼(X) 클릭 시 모달 닫기

5. [선택] 다른 문항의 '해설보기' 클릭
   → 이전 모달 닫고 새로운 문항의 해설 표시
   → 동일하게 클라이언트에서 처리
```

---

### 플로우 2: 중도 이탈 후 복원하여 완료

```
1. GET /quiz/available-educations
   → 풀 수 있는 퀴즈 목록 조회

2. GET /quiz/{eduId}/start
   → 퀴즈 시작 (새 시도 생성)
   → 응답: attemptId, questions[] (questionId, order, question, choices, answerIndex)

3. POST /quiz/attempt/{attemptId}/save
   → 일부 답안 임시 저장

4. POST /quiz/attempt/{attemptId}/leave
   → 이탈 기록 (페이지 이탈)

5. [나중에] GET /quiz/{eduId}/start
   → 퀴즈 재시작 (기존 미제출 시도 복원)
   → 응답: attemptId (동일), questions[] (저장된 답안 포함, userSelectedIndex는 복원됨)

6. POST /quiz/attempt/{attemptId}/save
   → 나머지 답안 저장

7. POST /quiz/attempt/{attemptId}/submit
   → 제출 및 채점

8. GET /quiz/attempt/{attemptId}/result
   → 결과 조회

9. GET /quiz/{attemptId}/wrongs
   → 오답노트 조회
```

---

### 플로우 3: 재응시 (두 번째 시도)

```
1. GET /quiz/available-educations
   → 풀 수 있는 퀴즈 목록 조회
   → 응답: 교육 목록 (hasAttempted: true, attemptCount: 1, bestScore: 70, passed: false)

2. [선택] GET /quiz/{eduId}/retry-info
   → 재응시 정보 조회 (재응시 가능 여부, 남은 횟수 등)
   → 응답: canRetry, currentAttemptCount, maxAttempts, remainingAttempts, bestScore, passed

3. 사용자가 같은 교육 선택 (재응시)

4. GET /quiz/{eduId}/start
   → 퀴즈 시작 (새 시도 생성, attemptNo: 2)
   → AI 서버에서 새로운 문항 생성

5. [풀이 과정...]

6. POST /quiz/attempt/{attemptId}/submit
   → 제출 및 채점
   → 응답: score, passed, correctCount, wrongCount, totalCount, submittedAt

7. GET /quiz/attempt/{attemptId}/result
   → 결과 조회 (점수 향상 여부 확인)
   → 응답: score, passed, passScore, correctCount, wrongCount, totalCount, finishedAt

8. GET /quiz/{attemptId}/wrongs
   → 오답노트 조회
```

---

### 플로우 4: 시간 만료로 자동 제출

```
1. GET /quiz/{eduId}/start
   → 퀴즈 시작

2. [반복] GET /quiz/attempt/{attemptId}/timer
   → 타이머 동기화
   → 응답: remainingSeconds 감소

3. GET /quiz/attempt/{attemptId}/timer
   → 타이머 조회
   → 응답: isExpired: true, remainingSeconds: 0

4. [자동] POST /quiz/attempt/{attemptId}/submit
   → 시간 만료로 자동 제출
   → 응답: score, passed, correctCount, wrongCount, totalCount, submittedAt

5. GET /quiz/attempt/{attemptId}/result
   → 결과 조회
```

---

### 플로우 5: 마이페이지에서 응시 내역 조회

```
1. GET /quiz/my-attempts
   → 내가 풀었던 퀴즈 응시 내역 조회
   → 응답: 시도 목록 (점수, 통과 여부, 최고 점수 여부)

2. 사용자가 특정 시도 선택

3. GET /quiz/attempt/{attemptId}/result
   → 결과 상세 조회

4. GET /quiz/{attemptId}/wrongs
   → 오답노트 조회
```

---

### 플로우 6: 타이머 동기화만 (백그라운드)

```
[퀴즈 풀이 화면 활성화 중]

주기적 호출 (예: 10초마다):
GET /quiz/attempt/{attemptId}/timer
→ 타이머 동기화
→ isExpired: true 감지 시 자동 제출 트리거
```

---

### 플로우 7: 재응시 정보 확인 후 재응시

```
1. GET /quiz/available-educations
   → 풀 수 있는 퀴즈 목록 조회
   → 응답: 교육 목록 (hasAttempted: true, attemptCount: 1, bestScore: 70, passed: false)

2. 사용자가 특정 교육 선택

3. GET /quiz/{eduId}/retry-info
   → 재응시 정보 조회
   → 응답: canRetry: true, currentAttemptCount: 1, maxAttempts: 3, remainingAttempts: 2, bestScore: 70, passed: false

4. 사용자가 재응시 결정

5. GET /quiz/{eduId}/start
   → 퀴즈 시작 (새 시도 생성)
   → [이후 플로우 1과 동일]
```

---

## 플로우별 특징

### 플로우 0: 퀴즈 초기 페이지

- **시작점**: 퀴즈 메인 페이지
- **특징**: 부서별 통계와 풀 수 있는 퀴즈 목록을 동시에 조회
- **API**: `department-stats`, `available-educations` (병렬 호출 가능)
- **표시 내용**: 부서별 점수판, 퀴즈 목록, 개인 점수 (미응시 시 "-")

### 플로우 1: 정상 응시

- **시작점**: 퀴즈 목록 화면
- **종료점**: 오답노트 화면
- **특징**: 모든 단계를 순차적으로 진행
- **퀴즈 풀이 페이지**: 모든 문항이 한 번에 로드되며, 프론트엔드에서 페이지네이션 처리
- **답안 저장**: 사용자가 답을 선택할 때마다 또는 주기적으로 임시 저장
- **타이머**: 주기적으로 동기화하여 시간 만료 시 자동 제출

### 플로우 2: 중도 이탈 후 복원

- **시작점**: 퀴즈 목록 화면
- **중단점**: 이탈 기록
- **복원점**: 다시 퀴즈 시작 (기존 시도 복원)
- **특징**: `start` API가 미제출 시도를 자동 복원

### 플로우 3: 재응시

- **시작점**: 퀴즈 목록 화면 (이미 응시한 교육)
- **특징**: 새로운 문항 생성 (attemptNo 증가)

### 플로우 4: 시간 만료

- **특징**: 타이머 만료 시 자동 제출
- **트리거**: `timer` API의 `isExpired: true`

### 플로우 5: 마이페이지 조회

- **시작점**: 마이페이지
- **특징**: 과거 응시 내역 조회 및 오답노트 확인

### 플로우 6: 타이머 동기화

- **특징**: 백그라운드에서 주기적 호출
- **목적**: 서버 시간과 동기화, 만료 감지

### 플로우 7: 재응시 정보 확인 후 재응시

- **시작점**: 퀴즈 목록 화면 (이미 응시한 교육)
- **특징**: 재응시 가능 여부 및 남은 횟수 확인 후 재응시 결정
- **API**: `retry-info`로 재응시 가능 여부 확인

### 플로우 8: 오답노트 조회 및 해설 확인

- **시작점**: 퀴즈 결과 화면 또는 마이페이지
- **목적**: 사용자가 틀린 문항과 정답, 해설을 확인하여 학습
- **API**: `wrongs` (단일 호출로 모든 오답 정보 획득)
- **특징**: 해설은 이미 받아온 데이터로 클라이언트에서 모달/오버레이로 표시
- **표시 내용**: 문제번호, 문제 내용, 해설보기 버튼, 해설 모달

---

## API 호출 패턴

### 필수 호출 순서

1. `GET /quiz/available-educations` (또는 `GET /quiz/my-attempts`)
2. `GET /quiz/{eduId}/start`
3. `POST /quiz/attempt/{attemptId}/submit`
4. `GET /quiz/attempt/{attemptId}/result`

### 선택적 호출

- `GET /quiz/attempt/{attemptId}/timer` - 타이머 동기화 (주기적)
- `POST /quiz/attempt/{attemptId}/save` - 답안 임시 저장 (답 선택 시)
- `POST /quiz/attempt/{attemptId}/leave` - 이탈 기록 (탭 전환 감지 시)
- `GET /quiz/{attemptId}/wrongs` - 오답노트 조회 (결과 화면에서)
- `GET /quiz/{eduId}/retry-info` - 재응시 정보 조회 (재응시 전 확인)
- `GET /quiz/department-stats` - 부서별 통계 조회 (관리자/통계 화면)

---

## 상태 전이

```
[퀴즈 목록]
    ↓
[퀴즈 시작] → attemptId 생성, questions[] 생성
    ↓
[퀴즈 풀이 중]
    ├─ 답안 임시 저장 (save)
    ├─ 타이머 동기화 (timer)
    ├─ 이탈 기록 (leave)
    └─ 제출 (submit) → submittedAt 설정
    ↓
[결과 화면] → result 조회
    ↓
[오답노트] → wrongs 조회
```

---

## 예외 케이스

### 1. 이미 제출한 시도 재시작 시도

- `GET /quiz/{eduId}/start` 호출
- → 새 시도 생성 (attemptNo 증가)

### 2. 미제출 시도 복원

- `GET /quiz/{eduId}/start` 호출
- → 기존 미제출 시도 자동 복원
- → 저장된 답안 포함하여 반환

### 3. 시간 만료

- `GET /quiz/attempt/{attemptId}/timer` → `isExpired: true`
- → 프론트에서 자동으로 `submit` 호출

### 4. 권한 없는 시도 접근

- 모든 API에서 `userUuid` 검증
- → 403 Forbidden 반환

### 5. 재응시 횟수 제한 초과

- `GET /quiz/{eduId}/retry-info` 호출
- → `canRetry: false` 반환
- → 재응시 불가능

### 6. 오답노트 조회 시도 권한 없음

- `GET /quiz/{attemptId}/wrongs` 호출
- → 다른 사용자의 시도 ID로 접근 시도
- → 403 Forbidden 반환

### 7. 미제출 시도에 대한 오답노트 조회

- `GET /quiz/{attemptId}/wrongs` 호출
- → 아직 제출하지 않은 시도 ID로 접근 시도
- → 409 Conflict 반환 ("attempt not submitted")
