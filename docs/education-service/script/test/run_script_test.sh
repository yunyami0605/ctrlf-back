#!/bin/bash

# Script API 테스트 스크립트

BASE_URL="${BASE_URL:-http://localhost:9002}"
INFRA_URL="${INFRA_URL:-http://localhost:9003}"
CLIENT_ID="${CLIENT_ID:-infra-admin}"
CLIENT_SECRET="${CLIENT_SECRET:-changeme}"
USERNAME="${USERNAME:-user1}"
PASSWORD="${PASSWORD:-11111}"

echo "=== Script API 테스트 시작 ==="

# 토큰 발급
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

echo "✓ Token 발급 성공"
echo ""

# Education ID와 Video ID 가져오기
EDUCATION_ID=$(curl -s -X GET "${BASE_URL}/admin/edus/with-videos" \
  -H "Authorization: Bearer ${TOKEN}" | jq -r '.[0].id // empty')

VIDEO_ID=$(curl -s -X GET "${BASE_URL}/admin/edus/with-videos" \
  -H "Authorization: Bearer ${TOKEN}" | jq -r '.[0].videos[0].id // empty')

if [ -z "$EDUCATION_ID" ]; then
  echo "ERROR: Education ID가 없습니다."
  exit 1
fi

echo "Education ID: $EDUCATION_ID"
echo "Video ID: $VIDEO_ID"
echo ""

# 1. 스크립트 ID 조회 (educationId)
echo "=== 1. 스크립트 ID 조회 (educationId) ==="
LOOKUP_RESPONSE=$(curl -s -X GET "${BASE_URL}/scripts/lookup?educationId=${EDUCATION_ID}" \
  -H "Authorization: Bearer ${TOKEN}")

echo "$LOOKUP_RESPONSE" | jq '.'

SCRIPT_ID=$(echo "$LOOKUP_RESPONSE" | jq -r '.scriptId // empty')

if [ -z "$SCRIPT_ID" ] || [ "$SCRIPT_ID" = "null" ]; then
  echo "⚠ WARNING: 스크립트가 없습니다."
  SCRIPT_ID=""
else
  echo "✓ Script ID: $SCRIPT_ID"
fi
echo ""

# 2. 스크립트 목록 조회
echo "=== 2. 스크립트 목록 조회 ==="
curl -s -X GET "${BASE_URL}/scripts?page=0&size=10" \
  -H "Authorization: Bearer ${TOKEN}" | jq '.[0:2]'
echo ""

# 3. 스크립트 상세 조회
if [ ! -z "$SCRIPT_ID" ]; then
  echo "=== 3. 스크립트 상세 조회 ==="
  curl -s -X GET "${BASE_URL}/scripts/${SCRIPT_ID}" \
    -H "Authorization: Bearer ${TOKEN}" | jq '.'
  echo ""
fi

echo "=== 테스트 완료 ==="
echo "⚠ 참고: 스크립트 수정, 삭제, 승인, 반려는 실제 스크립트가 있을 때만 테스트 가능합니다."

