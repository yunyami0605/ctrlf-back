package com.ctrlf.education.script.client;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * infra-service의 RAG 문서 API 호출 클라이언트 (RestClient 방식).
 */
@Component
public class InfraRagClient {

    private final RestClient restClient;
    private final String baseUrl;

    /**
     * RestClient를 구성하여 초기화합니다.
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
     * @throws org.springframework.web.client.RestClientException 네트워크/서버 오류 시
     */
    public DocumentTextResponse getText(String documentId) {
        return restClient.get()
            .uri("/rag/documents/{documentId}/text", documentId)
            .retrieve()
            .body(DocumentTextResponse.class);
    }

    /**
     * 문서 정보 조회.
     * 
     * @param documentId 문서 ID
     * @return 문서 정보 응답
     * @throws org.springframework.web.client.RestClientException 네트워크/서버 오류 시
     */
    public DocumentInfoResponse getDocument(String documentId) {
        return restClient.get()
            .uri("/rag/documents/{documentId}", documentId)
            .retrieve()
            .body(DocumentInfoResponse.class);
    }

    /**
     * S3 Presigned 다운로드 URL 조회.
     * 
     * @param fileUrl S3 파일 URL (s3://bucket/key 형식)
     * @return Presigned 다운로드 URL
     * @throws org.springframework.web.client.RestClientException 네트워크/서버 오류 시
     */
    public String getPresignedDownloadUrl(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return null;
        }
        try {
            PresignedDownloadResponse response = restClient.post()
                .uri("/infra/files/presign/download")
                .body(new PresignedDownloadRequest(fileUrl))
                .retrieve()
                .body(PresignedDownloadResponse.class);
            return response != null ? response.getDownloadUrl() : null;
        } catch (Exception e) {
            // presigned URL 생성 실패 시 원본 URL 반환 (fallback)
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
