#!/bin/bash

# Quiz API 테스트 스크립트

# 환경 변수 설정
BASE_URL="${BASE_URL:-http://localhost:9002}"
INFRA_URL="${INFRA_URL:-http://localhost:9003}"
CLIENT_ID="${CLIENT_ID:-infra-admin}"
CLIENT_SECRET="${CLIENT_SECRET:-changeme}"
USERNAME="${USERNAME:-user1}"
PASSWORD="${PASSWORD:-11111}"

# Education ID가 환경 변수로 제공되면 사용
if [ ! -z "$EDUCATION_ID" ]; then
  echo "Using provided EDUCATION_ID: $EDUCATION_ID"
fi

echo "=== Quiz API 테스트 시작 ==="
echo "Base URL: $BASE_URL"
echo "Infra Service URL: $INFRA_URL"
echo "Username: $USERNAME"
echo ""

# 토큰 발급 (infra-service 사용)
echo "=== 1. 토큰 발급 (infra-service) ==="
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
  echo "Infra Service URL과 인증 정보를 확인하세요."
  exit 1
fi

echo "✓ Token 발급 성공"
echo ""

# 1. 풀 수 있는 퀴즈 목록 조회
echo "=== 2. 풀 수 있는 퀴즈 목록 조회 ==="
AVAILABLE_RESPONSE=$(curl -s -X GET "${BASE_URL}/quiz/available-educations" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json")

echo "$AVAILABLE_RESPONSE" | jq '.'

EDUCATION_ID=$(echo "$AVAILABLE_RESPONSE" | jq -r '.[0].educationId // empty')

# 풀 수 있는 퀴즈가 없으면 전체 교육 목록에서 가져오기
if [ -z "$EDUCATION_ID" ] || [ "$EDUCATION_ID" = "null" ]; then
  echo "⚠ 풀 수 있는 퀴즈가 없습니다. 전체 교육 목록에서 가져옵니다..."
  ALL_EDUS_RESPONSE=$(curl -s -X GET "${BASE_URL}/admin/edus/with-videos" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json")
  
  # admin/edus/with-videos는 id 필드를 사용
  EDUCATION_ID=$(echo "$ALL_EDUS_RESPONSE" | jq -r '.[0].id // .[0].educationId // empty')
  
  if [ -z "$EDUCATION_ID" ] || [ "$EDUCATION_ID" = "null" ]; then
    echo "ERROR: 교육 데이터가 없습니다."
    echo ""
    echo "해결 방법:"
    echo "1. 교육 서비스를 실행하고 시드 데이터를 생성하세요:"
    echo "   SPRING_PROFILES_ACTIVE=local,local-seed ./gradlew :education-service:bootRun"
    echo ""
    echo "2. 또는 환경 변수로 educationId를 제공하세요:"
    echo "   export EDUCATION_ID=\"your-education-id\""
    echo "   ./run_quiz_test.sh"
    exit 1
  fi
  
  echo "✓ 전체 교육 목록에서 Education ID를 찾았습니다: $EDUCATION_ID"
else
  echo "✓ Education ID: $EDUCATION_ID (환경 변수에서 제공됨)"
fi
echo ""

# 2. 퀴즈 시작
echo "=== 3. 퀴즈 시작 ==="
START_RESPONSE=$(curl -s -X GET "${BASE_URL}/quiz/${EDUCATION_ID}/start" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json")

echo "$START_RESPONSE" | jq '.'

ATTEMPT_ID=$(echo "$START_RESPONSE" | jq -r '.attemptId // empty')
QUESTION_IDS=$(echo "$START_RESPONSE" | jq -r '.questions[].questionId' | tr '\n' ' ')

if [ -z "$ATTEMPT_ID" ] || [ "$ATTEMPT_ID" = "null" ]; then
  echo "ERROR: 퀴즈 시작 실패"
  exit 1
fi

echo "✓ Attempt ID: $ATTEMPT_ID"
echo "✓ Question IDs: $QUESTION_IDS"
echo ""

# 3. 타이머 정보 조회
echo "=== 4. 타이머 정보 조회 ==="
curl -s -X GET "${BASE_URL}/quiz/attempt/${ATTEMPT_ID}/timer" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" | jq '.'
echo ""

# 4. 응답 임시 저장
echo "=== 5. 응답 임시 저장 ==="
FIRST_QUESTION_ID=$(echo "$QUESTION_IDS" | awk '{print $1}')
if [ ! -z "$FIRST_QUESTION_ID" ]; then
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
    }" | jq '.'
else
  echo "⚠ WARNING: 저장할 답변이 없습니다."
fi
echo ""

# 5. 퀴즈 이탈 기록
echo "=== 6. 퀴즈 이탈 기록 ==="
TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
curl -s -X POST "${BASE_URL}/quiz/attempt/${ATTEMPT_ID}/leave" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{
    \"timestamp\": \"${TIMESTAMP}\",
    \"reason\": \"window_blur\",
    \"leaveSeconds\": 5
  }" | jq '.'
echo ""

# 6. 퀴즈 제출
echo "=== 7. 퀴즈 제출 ==="
if [ ! -z "$QUESTION_IDS" ]; then
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
    }" | jq '.'
else
  echo "⚠ WARNING: 제출할 답변이 없습니다."
fi
echo ""

# 7. 퀴즈 결과 조회
echo "=== 8. 퀴즈 결과 조회 ==="
curl -s -X GET "${BASE_URL}/quiz/attempt/${ATTEMPT_ID}/result" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" | jq '.'
echo ""

# 8. 오답노트 조회
echo "=== 9. 오답노트 조회 ==="
curl -s -X GET "${BASE_URL}/quiz/${ATTEMPT_ID}/wrongs" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" | jq '.'
echo ""

# 9. 내가 풀었던 퀴즈 응시 내역 조회
echo "=== 10. 내가 풀었던 퀴즈 응시 내역 조회 ==="
curl -s -X GET "${BASE_URL}/quiz/my-attempts" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" | jq '.'
echo ""

# 10. 퀴즈 재응시 정보 조회
echo "=== 11. 퀴즈 재응시 정보 조회 ==="
curl -s -X GET "${BASE_URL}/quiz/${EDUCATION_ID}/retry-info" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" | jq '.'
echo ""

# 11. 부서별 퀴즈 통계 조회
echo "=== 12. 부서별 퀴즈 통계 조회 ==="
curl -s -X GET "${BASE_URL}/quiz/department-stats" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" | jq '.'
echo ""

if [ ! -z "$EDUCATION_ID" ]; then
  echo "=== 12-1. 특정 교육 부서별 통계 조회 ==="
  curl -s -X GET "${BASE_URL}/quiz/department-stats?educationId=${EDUCATION_ID}" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" | jq '.'
  echo ""
fi

echo "=== 테스트 완료 ==="

