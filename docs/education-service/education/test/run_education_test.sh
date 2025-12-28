#!/bin/bash

# Education API 테스트 스크립트

# 환경 변수 설정
BASE_URL="${BASE_URL:-http://localhost:9002}"
INFRA_URL="${INFRA_URL:-http://localhost:9003}"
CLIENT_ID="${CLIENT_ID:-infra-admin}"
CLIENT_SECRET="${CLIENT_SECRET:-changeme}"
USERNAME="${USERNAME:-user1}"
PASSWORD="${PASSWORD:-11111}"

echo "=== Education API 테스트 시작 ==="
echo "Base URL: $BASE_URL"
echo "Infra Service URL: $INFRA_URL"
echo "Username: $USERNAME"
echo ""

# 토큰 발급 (infra-service)
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

# 1. 전체 교육 + 영상 목록 조회 (Admin)
echo "=== 2. 전체 교육 + 영상 목록 조회 (Admin) ==="
ALL_EDUS_RESPONSE=$(curl -s -X GET "${BASE_URL}/admin/edus/with-videos" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json")

echo "$ALL_EDUS_RESPONSE" | jq '.[0:2]'

EDUCATION_ID=$(echo "$ALL_EDUS_RESPONSE" | jq -r '.[0].id // empty')

if [ -z "$EDUCATION_ID" ] || [ "$EDUCATION_ID" = "null" ]; then
  echo "ERROR: 교육 데이터가 없습니다."
  exit 1
fi

echo "✓ Education ID: $EDUCATION_ID"
echo ""

# 2. 교육 상세 조회 (Admin)
echo "=== 3. 교육 상세 조회 (Admin) ==="
curl -s -X GET "${BASE_URL}/admin/edu/${EDUCATION_ID}" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" | jq '.'
echo ""

# 3. 교육 생성 (Admin)
echo "=== 4. 교육 생성 (Admin) ==="
NEW_EDUCATION_RESPONSE=$(curl -s -X POST "${BASE_URL}/admin/edu" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{
    \"title\": \"테스트 교육 $(date +%s)\",
    \"description\": \"테스트용 교육입니다\",
    \"category\": \"JOB_DUTY\",
    \"eduType\": \"MANDATORY\",
    \"require\": true,
    \"passScore\": 80,
    \"passRatio\": 90
  }")

echo "$NEW_EDUCATION_RESPONSE" | jq '.'

NEW_EDUCATION_ID=$(echo "$NEW_EDUCATION_RESPONSE" | jq -r '.id // empty')

if [ -z "$NEW_EDUCATION_ID" ] || [ "$NEW_EDUCATION_ID" = "null" ]; then
  echo "⚠ WARNING: 교육 생성 실패 또는 ID 추출 실패"
  NEW_EDUCATION_ID=""
else
  echo "✓ New Education ID: $NEW_EDUCATION_ID"
fi
echo ""

# 4. 교육 수정 (Admin)
if [ ! -z "$NEW_EDUCATION_ID" ]; then
  echo "=== 5. 교육 수정 (Admin) ==="
  curl -s -X PUT "${BASE_URL}/admin/edu/${NEW_EDUCATION_ID}" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -d "{
      \"title\": \"테스트 교육 (수정됨)\",
      \"description\": \"수정된 설명\",
      \"passScore\": 85
    }" | jq '.'
  echo ""
fi

# 5. 사용자 자신 교육 및 영상 목록 조회 (User)
echo "=== 6. 사용자 자신 교육 및 영상 목록 조회 (User) ==="
curl -s -X GET "${BASE_URL}/edus/me" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" | jq '.[0:2]'
echo ""

# 6. 교육 영상 목록 조회 (User)
echo "=== 7. 교육 영상 목록 조회 (User) ==="
VIDEOS_RESPONSE=$(curl -s -X GET "${BASE_URL}/edu/${EDUCATION_ID}/videos" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json")

echo "$VIDEOS_RESPONSE" | jq '.'

VIDEO_ID=$(echo "$VIDEOS_RESPONSE" | jq -r '.videos[0].id // empty')

if [ -z "$VIDEO_ID" ] || [ "$VIDEO_ID" = "null" ]; then
  echo "⚠ WARNING: 영상이 없습니다."
  VIDEO_ID=""
else
  echo "✓ Video ID: $VIDEO_ID"
fi
echo ""

# 7. 영상 시청 진행률 업데이트 (User)
if [ ! -z "$VIDEO_ID" ]; then
  echo "=== 8. 영상 시청 진행률 업데이트 (User) ==="
  curl -s -X POST "${BASE_URL}/edu/${EDUCATION_ID}/video/${VIDEO_ID}/progress" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -d "{
      \"position\": 120,
      \"duration\": 1800,
      \"watchTime\": 120
    }" | jq '.'
  echo ""
fi

# 8. 교육 이수 처리 (User) - 주의: 모든 영상 이수 필요
echo "=== 9. 교육 이수 처리 (User) ==="
echo "⚠ 주의: 모든 영상을 이수해야 교육 이수가 완료됩니다."
curl -s -X POST "${BASE_URL}/edu/${EDUCATION_ID}/complete" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" | jq '.'
echo ""

# 9. 교육 삭제 (Admin)
if [ ! -z "$NEW_EDUCATION_ID" ]; then
  echo "=== 10. 교육 삭제 (Admin) ==="
  curl -s -X DELETE "${BASE_URL}/admin/edu/${NEW_EDUCATION_ID}" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" | jq '.'
  echo ""
fi

echo "=== 테스트 완료 ==="

