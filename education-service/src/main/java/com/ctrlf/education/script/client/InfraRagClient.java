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
        @Value("${app.infra.base-url:http://localhost:9001}") String baseUrl
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
}
