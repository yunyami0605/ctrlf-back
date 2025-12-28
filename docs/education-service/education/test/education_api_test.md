# Education API 테스트 가이드

이 문서는 Education API를 curl을 사용하여 테스트하는 방법을 제공합니다.

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

### 1. 전체 교육 + 영상 목록 조회 (Admin)

```bash
echo "=== 1. 전체 교육 + 영상 목록 조회 ==="
curl -X GET "${BASE_URL}/admin/edus/with-videos" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" | jq

# 첫 번째 교육 ID 추출
EDUCATION_ID=$(curl -s -X GET "${BASE_URL}/admin/edus/with-videos" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" | jq -r '.[0].id // empty')

echo "Education ID: $EDUCATION_ID"
```

---

### 2. 교육 상세 조회 (Admin)

```bash
echo "=== 2. 교육 상세 조회 ==="
if [ -z "$EDUCATION_ID" ]; then
  echo "ERROR: Education ID가 없습니다."
  exit 1
fi

curl -X GET "${BASE_URL}/admin/edu/${EDUCATION_ID}" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" | jq
```

---

### 3. 교육 생성 (Admin)

```bash
echo "=== 3. 교육 생성 ==="
curl -X POST "${BASE_URL}/admin/edu" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "테스트 교육",
    "description": "테스트용 교육입니다",
    "category": "JOB_DUTY",
    "eduType": "MANDATORY",
    "require": true,
    "passScore": 80,
    "passRatio": 90
  }' | jq

# 생성된 교육 ID 추출
NEW_EDUCATION_ID=$(curl -s -X POST "${BASE_URL}/admin/edu" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "테스트 교육",
    "description": "테스트용 교육입니다",
    "category": "JOB_DUTY",
    "eduType": "MANDATORY",
    "require": true,
    "passScore": 80,
    "passRatio": 90
  }' | jq -r '.id // empty')

echo "New Education ID: $NEW_EDUCATION_ID"
```

---

### 4. 교육 수정 (Admin)

```bash
echo "=== 4. 교육 수정 ==="
if [ -z "$NEW_EDUCATION_ID" ]; then
  echo "ERROR: New Education ID가 없습니다."
  exit 1
fi

curl -X PUT "${BASE_URL}/admin/edu/${NEW_EDUCATION_ID}" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "테스트 교육 (수정됨)",
    "description": "수정된 설명",
    "passScore": 85
  }' | jq
```

---

### 5. 사용자 자신 교육 및 영상 목록 조회 (User)

```bash
echo "=== 5. 사용자 자신 교육 및 영상 목록 조회 ==="
curl -X GET "${BASE_URL}/edus/me" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" | jq

# Query Parameter 사용 예시
curl -X GET "${BASE_URL}/edus/me?completed=false&category=JOB_DUTY" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" | jq
```

---

### 6. 교육 영상 목록 조회 (User)

```bash
echo "=== 6. 교육 영상 목록 조회 ==="
if [ -z "$EDUCATION_ID" ]; then
  echo "ERROR: Education ID가 없습니다."
  exit 1
fi

curl -X GET "${BASE_URL}/edu/${EDUCATION_ID}/videos" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" | jq

# 첫 번째 영상 ID 추출
VIDEO_ID=$(curl -s -X GET "${BASE_URL}/edu/${EDUCATION_ID}/videos" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" | jq -r '.videos[0].id // empty')

echo "Video ID: $VIDEO_ID"
```

---

### 7. 영상 시청 진행률 업데이트 (User)

```bash
echo "=== 7. 영상 시청 진행률 업데이트 ==="
if [ -z "$EDUCATION_ID" ] || [ -z "$VIDEO_ID" ]; then
  echo "ERROR: Education ID 또는 Video ID가 없습니다."
  exit 1
fi

curl -X POST "${BASE_URL}/edu/${EDUCATION_ID}/video/${VIDEO_ID}/progress" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "position": 120,
    "duration": 1800,
    "watchTime": 120
  }' | jq
```

---

### 8. 교육 이수 처리 (User)

```bash
echo "=== 8. 교육 이수 처리 ==="
if [ -z "$EDUCATION_ID" ]; then
  echo "ERROR: Education ID가 없습니다."
  exit 1
fi

curl -X POST "${BASE_URL}/edu/${EDUCATION_ID}/complete" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" | jq
```

---

### 9. 교육 삭제 (Admin)

```bash
echo "=== 9. 교육 삭제 ==="
if [ -z "$NEW_EDUCATION_ID" ]; then
  echo "ERROR: New Education ID가 없습니다."
  exit 1
fi

curl -X DELETE "${BASE_URL}/admin/edu/${NEW_EDUCATION_ID}" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" | jq
```

---

## 전체 테스트 스크립트

모든 테스트를 순차적으로 실행하는 스크립트는 `run_education_test.sh`를 참고하세요.

---

## 주의사항

1. **토큰 만료**: Access Token은 만료될 수 있으므로, 테스트 중 401 에러가 발생하면 토큰을 재발급하세요.
2. **Education ID**: 실제 데이터베이스에 교육 데이터가 있어야 테스트가 가능합니다.
3. **순서**: 일부 테스트는 이전 테스트의 결과(예: `EDUCATION_ID`)에 의존하므로 순서대로 실행해야 합니다.
4. **권한**: Admin API는 `ROLE_ADMIN` 권한이 필요합니다.

---

## 에러 처리

### 401 Unauthorized
- 토큰이 만료되었거나 잘못되었습니다. 토큰을 재발급하세요.

### 403 Forbidden
- 권한이 부족합니다. Admin API는 `ROLE_ADMIN` 권한이 필요합니다.

### 404 Not Found
- 요청한 리소스(교육, 영상 등)가 존재하지 않습니다.

### 400 Bad Request
- 요청 형식이 잘못되었습니다. JSON 형식과 필수 필드를 확인하세요.

