# Quiz API 테스트 가이드

이 문서는 Quiz API를 curl을 사용하여 테스트하는 방법을 제공합니다.

## 사전 준비

### 1. 환경 변수 설정

```bash
# Base URL
BASE_URL="http://localhost:9002"
INFRA_URL="http://localhost:9003"

# Infra Service 설정 (토큰 발급용)
CLIENT_ID="infra-admin"
CLIENT_SECRET="changeme"
USERNAME="user1"      # 실제 사용자명으로 변경
PASSWORD="11111"      # 실제 비밀번호로 변경
```

### 2. Access Token 발급 (infra-service)

```bash
# infra-service에서 Access Token 발급
TOKEN=$(curl -s -X POST "${INFRA_URL}/admin/users/token/password" \
  -H "Content-Type: application/json" \
  -d "{
    \"clientId\": \"${CLIENT_ID}\",
    \"clientSecret\": \"${CLIENT_SECRET}\",
    \"username\": \"${USERNAME}\",
    \"password\": \"${PASSWORD}\",
    \"scope\": \"openid profile email\"
  }" | jq -r '.access_token')

echo "Token: $TOKEN"
```

---

## 테스트 시나리오

### 1. 풀 수 있는 퀴즈 목록 조회

```bash
echo "=== 1. 풀 수 있는 퀴즈 목록 조회 ==="
curl -X GET "${BASE_URL}/quiz/available-educations" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" | jq

# 응답에서 educationId 추출 (첫 번째 교육 ID 사용)
EDUCATION_ID=$(curl -s -X GET "${BASE_URL}/quiz/available-educations" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" | jq -r '.[0].educationId // empty')

echo "Education ID: $EDUCATION_ID"
```

**예상 응답**:
```json
[
  {
    "educationId": "550e8400-e29b-41d4-a716-446655440000",
    "title": "산업안전 교육",
    "category": "MANDATORY",
    "eduType": "JOB",
    "attemptCount": 0,
    "maxAttempts": 3,
    "hasAttempted": false,
    "bestScore": null,
    "passed": null
  }
]
```

---

### 2. 퀴즈 시작 (문항 생성/복원)

```bash
echo "=== 2. 퀴즈 시작 ==="
if [ -z "$EDUCATION_ID" ]; then
  echo "ERROR: Education ID가 없습니다. 먼저 1번 테스트를 실행하세요."
  exit 1
fi

RESPONSE=$(curl -s -X GET "${BASE_URL}/quiz/${EDUCATION_ID}/start" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json")

echo "$RESPONSE" | jq

# Attempt ID 추출
ATTEMPT_ID=$(echo "$RESPONSE" | jq -r '.attemptId // empty')
echo "Attempt ID: $ATTEMPT_ID"

# Question IDs 추출 (나중에 사용)
QUESTION_IDS=$(echo "$RESPONSE" | jq -r '.questions[].questionId' | tr '\n' ' ')
echo "Question IDs: $QUESTION_IDS"
```

**예상 응답**:
```json
{
  "attemptId": "550e8400-e29b-41d4-a716-446655440001",
  "questions": [
    {
      "questionId": "550e8400-e29b-41d4-a716-446655440002",
      "order": 0,
      "question": "산업안전의 목적은?",
      "choices": ["안전", "효율", "생산성"],
      "answerIndex": null
    }
  ]
}
```

---

### 3. 타이머 정보 조회

```bash
echo "=== 3. 타이머 정보 조회 ==="
if [ -z "$ATTEMPT_ID" ]; then
  echo "ERROR: Attempt ID가 없습니다. 먼저 2번 테스트를 실행하세요."
  exit 1
fi

curl -X GET "${BASE_URL}/quiz/attempt/${ATTEMPT_ID}/timer" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" | jq
```

**예상 응답**:
```json
{
  "timeLimit": 900,
  "startedAt": "2025-12-24T10:00:00Z",
  "expiresAt": "2025-12-24T10:15:00Z",
  "remainingSeconds": 600,
  "isExpired": false
}
```

---

### 4. 응답 임시 저장

```bash
echo "=== 4. 응답 임시 저장 ==="
if [ -z "$ATTEMPT_ID" ] || [ -z "$QUESTION_IDS" ]; then
  echo "ERROR: Attempt ID 또는 Question IDs가 없습니다."
  exit 1
fi

# 첫 번째 문항에 답변 저장
FIRST_QUESTION_ID=$(echo "$QUESTION_IDS" | awk '{print $1}')

curl -X POST "${BASE_URL}/quiz/attempt/${ATTEMPT_ID}/save" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{
    \"answers\": [
      {
        \"questionId\": \"${FIRST_QUESTION_ID}\",
        \"userSelectedIndex\": 0
      }
    ]
  }" | jq
```

**예상 응답**:
```json
{
  "saved": true,
  "savedCount": 1,
  "savedAt": "2025-12-24T10:00:30Z"
}
```

---

### 5. 퀴즈 이탈 기록

```bash
echo "=== 5. 퀴즈 이탈 기록 ==="
if [ -z "$ATTEMPT_ID" ]; then
  echo "ERROR: Attempt ID가 없습니다."
  exit 1
fi

TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

curl -X POST "${BASE_URL}/quiz/attempt/${ATTEMPT_ID}/leave" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{
    \"timestamp\": \"${TIMESTAMP}\",
    \"reason\": \"window_blur\",
    \"leaveSeconds\": 5
  }" | jq
```

**예상 응답**:
```json
{
  "recorded": true,
  "leaveCount": 1,
  "lastLeaveAt": "2025-12-24T10:00:35Z"
}
```

---

### 6. 퀴즈 제출/채점

```bash
echo "=== 6. 퀴즈 제출/채점 ==="
if [ -z "$ATTEMPT_ID" ] || [ -z "$QUESTION_IDS" ]; then
  echo "ERROR: Attempt ID 또는 Question IDs가 없습니다."
  exit 1
fi

# 모든 문항에 답변 생성 (예: 모두 첫 번째 선택지 선택)
ANSWERS_JSON="["
FIRST=true
for QID in $QUESTION_IDS; do
  if [ "$FIRST" = true ]; then
    FIRST=false
  else
    ANSWERS_JSON+=","
  fi
  ANSWERS_JSON+="{\"questionId\":\"${QID}\",\"userSelectedIndex\":0}"
done
ANSWERS_JSON+="]"

curl -X POST "${BASE_URL}/quiz/attempt/${ATTEMPT_ID}/submit" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{
    \"answers\": ${ANSWERS_JSON}
  }" | jq
```

**예상 응답**:
```json
{
  "score": 85,
  "passed": true,
  "correctCount": 17,
  "wrongCount": 3,
  "totalCount": 20,
  "submittedAt": "2025-12-24T10:15:00Z"
}
```

---

### 7. 퀴즈 결과 조회

```bash
echo "=== 7. 퀴즈 결과 조회 ==="
if [ -z "$ATTEMPT_ID" ]; then
  echo "ERROR: Attempt ID가 없습니다."
  exit 1
fi

curl -X GET "${BASE_URL}/quiz/attempt/${ATTEMPT_ID}/result" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" | jq
```

**예상 응답**:
```json
{
  "score": 85,
  "passed": true,
  "correctCount": 17,
  "wrongCount": 3,
  "totalCount": 20,
  "finishedAt": "2025-12-24T10:15:00Z"
}
```

---

### 8. 오답노트 목록 조회

```bash
echo "=== 8. 오답노트 목록 조회 ==="
if [ -z "$ATTEMPT_ID" ]; then
  echo "ERROR: Attempt ID가 없습니다."
  exit 1
fi

curl -X GET "${BASE_URL}/quiz/${ATTEMPT_ID}/wrongs" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" | jq
```

**예상 응답**:
```json
[
  {
    "question": "산업안전의 목적은?",
    "userAnswerIndex": 1,
    "correctAnswerIndex": 0,
    "explanation": "산업안전의 목적은...",
    "choices": ["안전", "효율", "생산성"]
  }
]
```

---

### 9. 내가 풀었던 퀴즈 응시 내역 조회

```bash
echo "=== 9. 내가 풀었던 퀴즈 응시 내역 조회 ==="
curl -X GET "${BASE_URL}/quiz/my-attempts" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" | jq
```

**예상 응답**:
```json
[
  {
    "attemptId": "550e8400-e29b-41d4-a716-446655440001",
    "educationId": "550e8400-e29b-41d4-a716-446655440000",
    "educationTitle": "산업안전 교육",
    "score": 85,
    "passed": true,
    "attemptNo": 1,
    "submittedAt": "2025-12-24T10:15:00Z",
    "isBestScore": true
  }
]
```

---

### 10. 퀴즈 재응시 정보 조회

```bash
echo "=== 10. 퀴즈 재응시 정보 조회 ==="
if [ -z "$EDUCATION_ID" ]; then
  echo "ERROR: Education ID가 없습니다."
  exit 1
fi

curl -X GET "${BASE_URL}/quiz/${EDUCATION_ID}/retry-info" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" | jq
```

**예상 응답**:
```json
{
  "educationId": "550e8400-e29b-41d4-a716-446655440000",
  "educationTitle": "산업안전 교육",
  "canRetry": true,
  "currentAttemptCount": 1,
  "maxAttempts": 3,
  "remainingAttempts": 2,
  "bestScore": 85,
  "passed": true,
  "lastAttemptAt": "2025-12-24T10:15:00Z"
}
```

---

### 11. 부서별 퀴즈 통계 조회

```bash
echo "=== 11. 부서별 퀴즈 통계 조회 ==="

# 전체 교육 대상
curl -X GET "${BASE_URL}/quiz/department-stats" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" | jq

# 특정 교육 대상
if [ ! -z "$EDUCATION_ID" ]; then
  curl -X GET "${BASE_URL}/quiz/department-stats?educationId=${EDUCATION_ID}" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" | jq
fi
```

**예상 응답**:
```json
[
  {
    "departmentName": "인사팀",
    "averageScore": 85,
    "progressPercent": 75,
    "participantCount": 50
  }
]
```

---

## 전체 테스트 스크립트

모든 테스트를 순차적으로 실행하는 스크립트:

```bash
#!/bin/bash

# 환경 변수 설정
BASE_URL="http://localhost:9002"
INFRA_URL="http://localhost:9003"
CLIENT_ID="infra-admin"
CLIENT_SECRET="changeme"
USERNAME="user1"
PASSWORD="11111"

# 토큰 발급 (infra-service)
echo "=== 토큰 발급 ==="
TOKEN=$(curl -s -X POST "${INFRA_URL}/admin/users/token/password" \
  -H "Content-Type: application/json" \
  -d "{
    \"clientId\": \"${CLIENT_ID}\",
    \"clientSecret\": \"${CLIENT_SECRET}\",
    \"username\": \"${USERNAME}\",
    \"password\": \"${PASSWORD}\",
    \"scope\": \"openid profile email\"
  }" | jq -r '.access_token')

if [ -z "$TOKEN" ] || [ "$TOKEN" = "null" ]; then
  echo "ERROR: 토큰 발급 실패"
  exit 1
fi

echo "Token 발급 성공"

# 1. 풀 수 있는 퀴즈 목록 조회
echo -e "\n=== 1. 풀 수 있는 퀴즈 목록 조회 ==="
EDUCATION_ID=$(curl -s -X GET "${BASE_URL}/quiz/available-educations" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" | jq -r '.[0].educationId // empty')

if [ -z "$EDUCATION_ID" ]; then
  echo "ERROR: 풀 수 있는 퀴즈가 없습니다."
  exit 1
fi

echo "Education ID: $EDUCATION_ID"

# 2. 퀴즈 시작
echo -e "\n=== 2. 퀴즈 시작 ==="
RESPONSE=$(curl -s -X GET "${BASE_URL}/quiz/${EDUCATION_ID}/start" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json")

ATTEMPT_ID=$(echo "$RESPONSE" | jq -r '.attemptId // empty')
QUESTION_IDS=$(echo "$RESPONSE" | jq -r '.questions[].questionId' | tr '\n' ' ')

if [ -z "$ATTEMPT_ID" ]; then
  echo "ERROR: 퀴즈 시작 실패"
  exit 1
fi

echo "Attempt ID: $ATTEMPT_ID"
echo "Question IDs: $QUESTION_IDS"

# 3. 타이머 정보 조회
echo -e "\n=== 3. 타이머 정보 조회 ==="
curl -s -X GET "${BASE_URL}/quiz/attempt/${ATTEMPT_ID}/timer" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" | jq

# 4. 응답 임시 저장
echo -e "\n=== 4. 응답 임시 저장 ==="
FIRST_QUESTION_ID=$(echo "$QUESTION_IDS" | awk '{print $1}')
curl -s -X POST "${BASE_URL}/quiz/attempt/${ATTEMPT_ID}/save" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{
    \"answers\": [
      {
        \"questionId\": \"${FIRST_QUESTION_ID}\",
        \"userSelectedIndex\": 0
      }
    ]
  }" | jq

# 5. 퀴즈 이탈 기록
echo -e "\n=== 5. 퀴즈 이탈 기록 ==="
TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
curl -s -X POST "${BASE_URL}/quiz/attempt/${ATTEMPT_ID}/leave" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{
    \"timestamp\": \"${TIMESTAMP}\",
    \"reason\": \"window_blur\",
    \"leaveSeconds\": 5
  }" | jq

# 6. 퀴즈 제출
echo -e "\n=== 6. 퀴즈 제출 ==="
ANSWERS_JSON="["
FIRST=true
for QID in $QUESTION_IDS; do
  if [ "$FIRST" = true ]; then
    FIRST=false
  else
    ANSWERS_JSON+=","
  fi
  ANSWERS_JSON+="{\"questionId\":\"${QID}\",\"userSelectedIndex\":0}"
done
ANSWERS_JSON+="]"

curl -s -X POST "${BASE_URL}/quiz/attempt/${ATTEMPT_ID}/submit" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{
    \"answers\": ${ANSWERS_JSON}
  }" | jq

# 7. 퀴즈 결과 조회
echo -e "\n=== 7. 퀴즈 결과 조회 ==="
curl -s -X GET "${BASE_URL}/quiz/attempt/${ATTEMPT_ID}/result" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" | jq

# 8. 오답노트 조회
echo -e "\n=== 8. 오답노트 조회 ==="
curl -s -X GET "${BASE_URL}/quiz/${ATTEMPT_ID}/wrongs" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" | jq

# 9. 내가 풀었던 퀴즈 응시 내역 조회
echo -e "\n=== 9. 내가 풀었던 퀴즈 응시 내역 조회 ==="
curl -s -X GET "${BASE_URL}/quiz/my-attempts" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" | jq

# 10. 퀴즈 재응시 정보 조회
echo -e "\n=== 10. 퀴즈 재응시 정보 조회 ==="
curl -s -X GET "${BASE_URL}/quiz/${EDUCATION_ID}/retry-info" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" | jq

# 11. 부서별 퀴즈 통계 조회
echo -e "\n=== 11. 부서별 퀴즈 통계 조회 ==="
curl -s -X GET "${BASE_URL}/quiz/department-stats" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" | jq

echo -e "\n=== 테스트 완료 ==="
```

---

## 주의사항

1. **토큰 만료**: Access Token은 만료될 수 있으므로, 테스트 중 401 에러가 발생하면 토큰을 재발급하세요.
2. **Education ID**: 실제 데이터베이스에 교육 데이터가 있어야 테스트가 가능합니다.
3. **AI 서버**: 퀴즈 시작 시 AI 서버가 문항을 생성하므로, AI 서버가 실행 중이어야 합니다.
4. **순서**: 일부 테스트는 이전 테스트의 결과(예: `ATTEMPT_ID`)에 의존하므로 순서대로 실행해야 합니다.
5. **토큰 발급**: infra-service(`http://localhost:9003`)를 통해 토큰을 발급받습니다.

---

## 에러 처리

### 401 Unauthorized
- 토큰이 만료되었거나 잘못되었습니다. 토큰을 재발급하세요.

### 404 Not Found
- 요청한 리소스(교육, 시도 등)가 존재하지 않습니다. 먼저 데이터를 생성하세요.

### 400 Bad Request
- 요청 형식이 잘못되었습니다. JSON 형식과 필수 필드를 확인하세요.

