# S3 Presigned URL API 명세

**Base URL**: `/infra/files/presign`

---

## 1. Presigned Upload URL 발급

파일 업로드를 위한 Presigned URL을 발급합니다.

### Request

```
POST /infra/files/presign/upload
Content-Type: application/json
```

### Request Body

| 필드          | 타입   | 필수 | 설명                                             | 예시          |
| ------------- | ------ | ---- | ------------------------------------------------ | ------------- |
| `filename`    | string | ✅   | 원본 파일명                                      | `"test.png"`  |
| `contentType` | string | ✅   | MIME 타입                                        | `"image/png"` |
| `type`        | string | ✅   | 파일 카테고리 (`image`, `docs`, `video` 중 하나) | `"image"`     |

```json
{
  "filename": "test.png",
  "contentType": "image/png",
  "type": "image"
}
```

### Response (200 OK)

| 필드        | 타입   | 설명                                              |
| ----------- | ------ | ------------------------------------------------- |
| `uploadUrl` | string | 클라이언트가 PUT 업로드할 Presigned URL           |
| `fileUrl`   | string | 업로드 완료 후 저장될 S3 경로 (`s3://bucket/key`) |

```json
{
  "uploadUrl": "https://s3.ap-northeast-2.amazonaws.com/bucket/image/uuid-test.png?X-Amz-...",
  "fileUrl": "s3://bucket/image/uuid-test.png"
}
```

### 사용 방법

1. 이 API로 `uploadUrl` 발급
2. 클라이언트에서 `uploadUrl`로 **PUT** 요청하여 파일 업로드
3. 업로드 완료 후 `fileUrl`을 백엔드에 전달 (예: RAG 문서 등록)

---

## 2. Presigned Download URL 발급

파일 다운로드를 위한 Presigned URL을 발급합니다.

### Request

```
POST /infra/files/presign/download
Content-Type: application/json
```

### Request Body

| 필드      | 타입   | 필수 | 설명                             | 예시                           |
| --------- | ------ | ---- | -------------------------------- | ------------------------------ |
| `fileUrl` | string | ✅   | S3 파일 경로 (`s3://bucket/key`) | `"s3://ctrl-s3/docs/file.pdf"` |

```json
{
  "fileUrl": "s3://ctrl-s3/docs/file.pdf"
}
```

### Response (200 OK)

| 필드          | 타입   | 설명                              |
| ------------- | ------ | --------------------------------- |
| `downloadUrl` | string | GET으로 접근 가능한 Presigned URL |

```json
{
  "downloadUrl": "https://s3.ap-northeast-2.amazonaws.com/bucket/docs/file.pdf?X-Amz-..."
}
```

---

## 3. 서버 프록시 업로드 (테스트용)

서버가 Presigned URL로 파일을 업로드합니다. (프론트엔드 테스트용)

### Request

```
POST /infra/files/presign/upload/put?url={presignedUrl}
Content-Type: multipart/form-data
```

### Query Parameters

| 필드  | 타입   | 필수 | 설명              |
| ----- | ------ | ---- | ----------------- |
| `url` | string | ✅   | Presigned PUT URL |

### Form Data

| 필드   | 타입 | 필수 | 설명          |
| ------ | ---- | ---- | ------------- |
| `file` | file | ✅   | 업로드할 파일 |

### Response (200 OK)

```
uploaded
```

---

## 파일 타입 (type)

| 값      | 설명        | 용도                     |
| ------- | ----------- | ------------------------ |
| `image` | 이미지 파일 | 프로필 이미지, 썸네일 등 |
| `docs`  | 문서 파일   | PDF, PPT 등 교육 자료    |
| `video` | 영상 파일   | 생성된 교육 영상         |

---

## 업로드 플로우

```
┌─────────────┐     1. POST /upload      ┌─────────────┐
│   Frontend  │ ───────────────────────► │   Backend   │
│             │ ◄─────────────────────── │             │
│             │   uploadUrl, fileUrl     │             │
└─────────────┘                          └─────────────┘
       │
       │ 2. PUT uploadUrl (파일 바이너리)
       ▼
┌─────────────┐
│     S3      │
└─────────────┘
       │
       │ 3. fileUrl을 백엔드에 전달
       ▼
┌─────────────┐
│   Backend   │  (예: POST /rag/documents/upload)
└─────────────┘
```

---

## cURL 예시

### 1. Upload URL 발급

```bash
curl -X POST 'http://localhost:9003/infra/files/presign/upload' \
  -H 'Content-Type: application/json' \
  -d '{
    "filename": "education-material.pdf",
    "contentType": "application/pdf",
    "type": "docs"
  }'
```

### 2. S3에 파일 업로드

```bash
curl -X PUT '{uploadUrl}' \
  -H 'Content-Type: application/pdf' \
  --data-binary '@/path/to/education-material.pdf'
```

### 3. Download URL 발급

```bash
curl -X POST 'http://localhost:9003/infra/files/presign/download' \
  -H 'Content-Type: application/json' \
  -d '{
    "fileUrl": "s3://ctrl-s3/docs/uuid-education-material.pdf"
  }'
```
