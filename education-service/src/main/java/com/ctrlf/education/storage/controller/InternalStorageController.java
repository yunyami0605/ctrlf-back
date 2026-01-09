package com.ctrlf.education.storage.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * AI 서버(Storage Provider = backend_presigned) 호환용 내부 Storage API.
 *
 * AI는 아래 엔드포인트를 호출한다:
 * - POST /internal/storage/presign-put
 * - POST /internal/storage/complete
 * - POST /internal/storage/delete (옵션)
 *
 * 실제 presign 발급은 infra-service의 기존 API(/infra/files/presign/upload)를 사용한다.
 * (주의) infra-service는 key를 랜덤 생성하므로, object_key는 추적용으로만 취급한다.
 */
@RestController
@RequestMapping("/internal/storage")
@RequiredArgsConstructor
public class InternalStorageController {

    private final RestClient.Builder restClientBuilder;

    @Value("${app.infra.base-url:http://localhost:9003}")
    private String infraBaseUrl;

    @PostMapping("/presign-put")
    public ResponseEntity<PresignPutResponse> presignPut(@Valid @RequestBody PresignPutRequest req) {
        String baseUrl = normalizeBaseUrl(infraBaseUrl);
        RestClient client = restClientBuilder.baseUrl(baseUrl).build();

        String fileName = extractFileName(req.getObjectKey());
        String type = guessTypeFromFileName(fileName);

        // infra-service: POST /infra/files/presign/upload
        // req: {fileName, contentType, type}
        @SuppressWarnings("unchecked")
        Map<String, Object> infraResp = client.post()
            .uri("/infra/files/presign/upload")
            .body(Map.of(
                "fileName", fileName,
                "contentType", req.getContentType(),
                "type", type
            ))
            .retrieve()
            .body(Map.class);

        if (infraResp == null || infraResp.get("uploadUrl") == null || infraResp.get("fileUrl") == null) {
            throw new IllegalStateException("infra presign response invalid: " + infraResp);
        }

        String uploadUrl = infraResp.get("uploadUrl").toString();
        String publicUrl = infraResp.get("fileUrl").toString(); // s3://bucket/key

        // AI의 PUT 요청에 필요한 헤더 (최소 Content-Type)
        Map<String, String> headers = Map.of("Content-Type", req.getContentType());

        return ResponseEntity.ok(new PresignPutResponse(uploadUrl, publicUrl, headers, 36000));
    }

    @PostMapping("/complete")
    public ResponseEntity<Void> complete(@Valid @RequestBody CompleteRequest req) {
        // 현재는 무상태(호환용) - 필요 시 메타 저장/감사로그로 확장
        return ResponseEntity.ok().build();
    }

    @PostMapping("/delete")
    public ResponseEntity<Map<String, Object>> delete(@Valid @RequestBody DeleteRequest req) {
        // infra-service에 delete API가 없으므로, 현재는 호환용 no-op 처리
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    private static String normalizeBaseUrl(String url) {
        if (url == null) return "";
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String extractFileName(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) return "video.mp4";
        int slash = objectKey.lastIndexOf('/');
        return slash >= 0 ? objectKey.substring(slash + 1) : objectKey;
    }

    private static String guessTypeFromFileName(String fileName) {
        String lower = (fileName == null ? "" : fileName.toLowerCase());
        if (lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".webm")) return "video";
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp"))
            return "image";
        return "docs";
    }

    // ===== DTOs =====

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PresignPutRequest {
        @NotBlank private String objectKey;
        @NotBlank private String contentType;
        private long contentLength;
    }

    @Getter
    @AllArgsConstructor
    public static class PresignPutResponse {
        private String uploadUrl;
        private String publicUrl;
        private Map<String, String> headers;
        private int expiresSec;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompleteRequest {
        @NotBlank private String objectKey;
        @NotBlank private String etag;
        private long sizeBytes;
        @NotBlank private String contentType;
        @NotBlank private String publicUrl;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeleteRequest {
        @NotBlank private String objectKey;
    }
}


