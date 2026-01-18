package com.ctrlf.education.script.client;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * infra-service의 RAG 문서 API 호출 클라이언트 (RestClient 방식).
 */
@Component
public class InfraRagClient {

    private static final Logger log = LoggerFactory.getLogger(InfraRagClient.class);

    private final RestClient restClient;
    private final String baseUrl;

    /**
     * RestClient를 구성하여 초기화
     * 
     * @param baseUrl infra-service 베이스 URL
     */
    public InfraRagClient(
        @Value("${ctrlf.infra.base-url:http://localhost:9003}") String baseUrl
    ) {
        String normalizedUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.baseUrl = normalizedUrl;
        RestClient.Builder builder = RestClient.builder().baseUrl(normalizedUrl);
        this.restClient = builder.build();
    }

    /**
     * Base URL을 반환합니다.
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * 문서 원문 텍스트 조회.
     * 
     * @param documentId 문서 ID (materialId)
     * @return 원문 텍스트 응답
     * @throws RestClientException 네트워크/서버 오류 시
     */
    public DocumentTextResponse getText(String documentId) {
        try {
            log.info("문서 원문 텍스트 조회 요청: documentId={}", documentId);

            DocumentTextResponse response = restClient.get()
                .uri("/rag/documents/{documentId}/text", documentId)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), (req, res) -> {
                    String errorBody = "";
                    try {
                        errorBody = res.getBody() != null ? res.getBody().toString() : "";
                    } catch (Exception e) {
                        // ignore
                    }
                    log.error("문서 원문 텍스트 조회 실패: status={}, documentId={}, errorBody={}",
                        res.getStatusCode(), documentId, errorBody);
                    throw new RestClientException(
                        String.format("infra-service 오류: HTTP %s - %s", res.getStatusCode(), errorBody)
                    );
                })
                .body(DocumentTextResponse.class);

            log.info("문서 원문 텍스트 조회 응답: documentId={}, textLength={}",
                documentId, response != null && response.getText() != null ? response.getText().length() : 0);

            return response;
        } catch (RestClientException e) {
            log.error("문서 원문 텍스트 조회 요청 실패: documentId={}, error={}",
                documentId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 문서 정보 조회.
     * 
     * @param documentId 문서 ID
     * @return 문서 정보 응답
     * @throws RestClientException 네트워크/서버 오류 시
     */
    public DocumentInfoResponse getDocument(String documentId) {
        try {
            log.info("문서 정보 조회 요청: documentId={}", documentId);

            DocumentInfoResponse response = restClient.get()
                .uri("/rag/documents/{documentId}", documentId)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), (req, res) -> {
                    String errorBody = "";
                    try {
                        errorBody = res.getBody() != null ? res.getBody().toString() : "";
                    } catch (Exception e) {
                        // ignore
                    }
                    log.error("문서 정보 조회 실패: status={}, documentId={}, errorBody={}",
                        res.getStatusCode(), documentId, errorBody);
                    throw new RestClientException(
                        String.format("infra-service 오류: HTTP %s - %s", res.getStatusCode(), errorBody)
                    );
                })
                .body(DocumentInfoResponse.class);

            log.info("문서 정보 조회 응답: documentId={}, title={}, domain={}, status={}",
                documentId,
                response != null ? response.getTitle() : null,
                response != null ? response.getDomain() : null,
                response != null ? response.getStatus() : null);

            return response;
        } catch (RestClientException e) {
            log.error("문서 정보 조회 요청 실패: documentId={}, error={}",
                documentId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * S3 Presigned 다운로드 URL 조회.
     * 
     * @param fileUrl S3 파일 URL (s3://bucket/key 형식)
     * @return Presigned 다운로드 URL (실패 시 원본 fileUrl 반환)
     */
    public String getPresignedDownloadUrl(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return null;
        }
        try {
            log.debug("Presigned 다운로드 URL 조회 요청: fileUrl={}", fileUrl);

            PresignedDownloadResponse response = restClient.post()
                .uri("/infra/files/presign/download")
                .body(new PresignedDownloadRequest(fileUrl))
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), (req, res) -> {
                    String errorBody = "";
                    try {
                        errorBody = res.getBody() != null ? res.getBody().toString() : "";
                    } catch (Exception e) {
                        // ignore
                    }
                    log.warn("Presigned 다운로드 URL 조회 실패: status={}, fileUrl={}, errorBody={}",
                        res.getStatusCode(), fileUrl, errorBody);
                    throw new RestClientException(
                        String.format("infra-service 오류: HTTP %s - %s", res.getStatusCode(), errorBody)
                    );
                })
                .body(PresignedDownloadResponse.class);

            String downloadUrl = response != null ? response.getDownloadUrl() : null;
            log.debug("Presigned 다운로드 URL 조회 응답: fileUrl={}, success={}", fileUrl, downloadUrl != null);
            return downloadUrl;
        } catch (Exception e) {
            // presigned URL 생성 실패 시 원본 URL 반환 (fallback)
            log.warn("Presigned 다운로드 URL 생성 실패, 원본 URL 반환: fileUrl={}, error={}",
                fileUrl, e.getMessage());
            return fileUrl;
        }
    }

    /**
     * 문서 원문 텍스트 응답 DTO.
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentTextResponse {
        private String documentId;
        private String text;
    }

    /**
     * 문서 정보 응답 DTO.
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentInfoResponse {
        private String id;
        private String title;
        private String domain;
        private String sourceUrl; // fileUrl
        private String status;
        private Integer version;  // 문서 버전
    }

    /**
     * Presigned 다운로드 URL 요청 DTO.
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PresignedDownloadRequest {
        private String fileUrl;
    }

    /**
     * Presigned 다운로드 URL 응답 DTO.
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PresignedDownloadResponse {
        private String downloadUrl;
    }
}
