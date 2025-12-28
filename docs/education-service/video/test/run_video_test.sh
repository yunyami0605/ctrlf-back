#!/bin/bash

# Video API 테스트 스크립트

BASE_URL="${BASE_URL:-http://localhost:9002}"
INFRA_URL="${INFRA_URL:-http://localhost:9003}"
CLIENT_ID="${CLIENT_ID:-infra-admin}"
CLIENT_SECRET="${CLIENT_SECRET:-changeme}"
USERNAME="${USERNAME:-user1}"
PASSWORD="${PASSWORD:-11111}"

echo "=== Video API 테스트 시작 ==="

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

# Education ID 가져오기
EDUCATION_ID=$(curl -s -X GET "${BASE_URL}/admin/edus/with-videos" \
  -H "Authorization: Bearer ${TOKEN}" | jq -r '.[0].id // empty')

if [ -z "$EDUCATION_ID" ]; then
  echo "ERROR: Education ID가 없습니다."
  exit 1
fi

echo "Education ID: $EDUCATION_ID"
echo ""

# 1. 영상 목록 조회 (Admin)
echo "=== 1. 영상 목록 조회 (Admin) ==="
VIDEOS_RESPONSE=$(curl -s -X GET "${BASE_URL}/admin/videos/list?page=0&size=10" \
  -H "Authorization: Bearer ${TOKEN}")

echo "$VIDEOS_RESPONSE" | jq '.[0:2]'

VIDEO_ID=$(echo "$VIDEOS_RESPONSE" | jq -r '.[0].id // empty')

if [ -z "$VIDEO_ID" ] || [ "$VIDEO_ID" = "null" ]; then
  echo "⚠ WARNING: 영상이 없습니다. 새로 생성합니다."
  
  # 2. 영상 생성
  echo "=== 2. 영상 컨텐츠 생성 (Admin) ==="
  CREATE_RESPONSE=$(curl -s -X POST "${BASE_URL}/admin/videos" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -d "{
      \"educationId\": \"${EDUCATION_ID}\",
      \"title\": \"테스트 영상 $(date +%s)\",
      \"departmentScope\": null
    }")
  
  echo "$CREATE_RESPONSE" | jq '.'
  
  VIDEO_ID=$(echo "$CREATE_RESPONSE" | jq -r '.videoId // empty')
  
  if [ -z "$VIDEO_ID" ] || [ "$VIDEO_ID" = "null" ]; then
    echo "ERROR: 영상 생성 실패"
    exit 1
  fi
  
  echo "✓ Video ID: $VIDEO_ID"
else
  echo "✓ Video ID: $VIDEO_ID"
fi
echo ""

# 3. 영상 상세 조회
echo "=== 3. 영상 상세 조회 ==="
curl -s -X GET "${BASE_URL}/video/${VIDEO_ID}" \
  -H "Authorization: Bearer ${TOKEN}" | jq '.'
echo ""

# 4. 영상 수정 (Admin)
echo "=== 4. 영상 수정 (Admin) ==="
curl -s -X PUT "${BASE_URL}/admin/videos/${VIDEO_ID}" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{
    \"title\": \"수정된 영상 제목\"
  }" | jq '.'
echo ""

echo "=== 테스트 완료 ==="
echo "⚠ 참고: 검토 요청, 승인, 게시 등은 영상 상태에 따라 테스트 가능합니다."

