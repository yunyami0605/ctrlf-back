# 📘 **RAG 문서 목록 조회**

## ✔ url

**GET /rag/documents**

---

### ✔ 설명

등록된 문서 목록을 조회한다.

필터 및 페이징 기능 지원:

- domain (HR/보안/개발 등)
- uploader
- date range
- keyword(문서명 검색)
- page / size

---

### ✔ 권한

`ROLE_ADMIN`, `ROLE_RAG_MANAGER`

---

# 📌 **Query Parameter**

| key          | 설명        | 타입         | Nullable | 예시           |
| ------------ | ----------- | ------------ | -------- | -------------- |
| domain       | 문서 도메인 | string       | true     | `"HR"`         |
| uploaderUuid | 업로더 UUID | string(uuid) | true     | `"3a9e...”`    |
| startDate    | 기간 시작   | string(date) | true     | `"2025-01-01"` |
| endDate      | 기간 끝     | string(date) | true     | `"2025-12-31"` |
| keyword      | 제목 검색   | string       | true     | `"안전"`       |
| page         | 페이지 번호 | number       | false    | `0`            |
| size         | 페이지 크기 | number       | false    | `10`           |

---

# 📌 **Response**

| key          | 설명        | 타입             | 예시                    |
| ------------ | ----------- | ---------------- | ----------------------- |
| (array)      | 문서 목록   | array            | `[ {...} ]`             |
| id           | RAG 문서 ID | number           | `101`                   |
| title        | 문서 제목   | string           | `"산업안전 규정집"`     |
| domain       | 문서 도메인 | string           | `"HR"`                  |
| uploaderUuid | 업로더 UUID | string(uuid)     | `"c1aa..."`             |
| createdAt    | 등록 시각   | string(datetime) | `"2025-01-01T12:00:00"` |

---

### 📌 Example

```json
[
  {
    "id": 101,
    "title": "산업안전 규정집 v3",
    "domain": "HR",
    "uploaderUuid": "c13c91f2-fb1a-4d42-b381-72847a52fb99",
    "createdAt": "2025-01-01T12:00:00"
  },
  {
    "id": 102,
    "title": "보안 정책 매뉴얼",
    "domain": "SECURITY",
    "uploaderUuid": "fa932...",
    "createdAt": "2025-01-02T16:21:10"
  }
]
```

---

# 📌 **Status**

| status                        | 설명                |
| ----------------------------- | ------------------- |
| **200 OK**                    | 문서 목록 조회 성공 |
| **400 Bad Request**           | 잘못된 필터 값      |
| **401 Unauthorized**          | 인증 실패           |
| **403 Forbidden**             | 권한 없음           |
| **500 Internal Server Error** | 서버 오류           |
