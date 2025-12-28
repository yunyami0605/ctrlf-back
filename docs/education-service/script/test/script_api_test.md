# Script API 테스트 가이드

이 문서는 Script API를 curl을 사용하여 테스트하는 방법을 제공합니다.

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

### 1. 스크립트 ID 조회

```bash
# educationId로 조회
curl -X GET "${BASE_URL}/scripts/lookup?educationId=${EDUCATION_ID}" \
  -H "Authorization: Bearer ${TOKEN}" | jq

# videoId로 조회
curl -X GET "${BASE_URL}/scripts/lookup?videoId=${VIDEO_ID}" \
  -H "Authorization: Bearer ${TOKEN}" | jq
```

### 2. 스크립트 목록 조회

```bash
curl -X GET "${BASE_URL}/scripts?page=0&size=10" \
  -H "Authorization: Bearer ${TOKEN}" | jq
```

### 3. 스크립트 상세 조회

```bash
curl -X GET "${BASE_URL}/scripts/${SCRIPT_ID}" \
  -H "Authorization: Bearer ${TOKEN}" | jq
```

### 4. 스크립트 수정 (Admin)

```bash
curl -X PUT "${BASE_URL}/scripts/${SCRIPT_ID}" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "script": "{\"title\": \"수정된 스크립트\"}"
  }' | jq
```

### 5. 스크립트 삭제 (Admin)

```bash
curl -X DELETE "${BASE_URL}/scripts/${SCRIPT_ID}" \
  -H "Authorization: Bearer ${TOKEN}"
```

### 6. 스크립트 1차 승인 (Admin)

```bash
curl -X POST "${BASE_URL}/scripts/${SCRIPT_ID}/approve" \
  -H "Authorization: Bearer ${TOKEN}" | jq
```

### 7. 스크립트 1차 반려 (Admin)

```bash
curl -X POST "${BASE_URL}/scripts/${SCRIPT_ID}/reject" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "reason": "내용 수정 필요"
  }' | jq
```

---

## 전체 테스트 스크립트

`run_script_test.sh`를 참고하세요.

