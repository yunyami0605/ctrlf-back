# Video API 테스트 가이드

이 문서는 Video API를 curl을 사용하여 테스트하는 방법을 제공합니다.

## 사전 준비

### 1. 환경 변수 설정

```bash
BASE_URL="http://localhost:9002"
INFRA_URL="http://localhost:9003"
CLIENT_ID="infra-admin"
CLIENT_SECRET="changeme"
USERNAME="user1"
PASSWORD="11111"
```

### 2. Access Token 발급

```bash
TOKEN=$(curl -s -X POST "${INFRA_URL}/admin/users/token/password" \
  -H "Content-Type: application/json" \
  -d "{
    \"clientId\": \"${CLIENT_ID}\",
    \"clientSecret\": \"${CLIENT_SECRET}\",
    \"username\": \"${USERNAME}\",
    \"password\": \"${PASSWORD}\",
    \"scope\": \"openid profile email\"
  }" | jq -r '.access_token')
```

---

## 테스트 시나리오

### 1. 영상 컨텐츠 생성 (Admin)

```bash
curl -X POST "${BASE_URL}/admin/videos" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{
    \"educationId\": \"${EDUCATION_ID}\",
    \"title\": \"테스트 영상\",
    \"departmentScope\": null
  }" | jq
```

### 2. 영상 목록 조회 (Admin)

```bash
curl -X GET "${BASE_URL}/admin/videos/list?page=0&size=10" \
  -H "Authorization: Bearer ${TOKEN}" | jq
```

### 3. 영상 상세 조회

```bash
curl -X GET "${BASE_URL}/video/${VIDEO_ID}" \
  -H "Authorization: Bearer ${TOKEN}" | jq
```

### 4. 영상 수정 (Admin)

```bash
curl -X PUT "${BASE_URL}/admin/videos/${VIDEO_ID}" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "수정된 영상 제목"
  }' | jq
```

### 5. 검토 요청 (Admin)

```bash
curl -X PUT "${BASE_URL}/admin/videos/${VIDEO_ID}/review-request" \
  -H "Authorization: Bearer ${TOKEN}" | jq
```

### 6. 검토 승인 (Admin)

```bash
curl -X PUT "${BASE_URL}/admin/videos/${VIDEO_ID}/approve" \
  -H "Authorization: Bearer ${TOKEN}" | jq
```

### 7. 게시 (Admin)

```bash
curl -X PUT "${BASE_URL}/admin/videos/${VIDEO_ID}" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "status": "PUBLISHED"
  }' | jq
```

---

## 전체 테스트 스크립트

`run_video_test.sh`를 참고하세요.

