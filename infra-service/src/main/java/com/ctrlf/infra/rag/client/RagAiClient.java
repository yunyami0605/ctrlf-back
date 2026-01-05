package com.ctrlf.infra.rag.client;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * AI 서버(RAG 파이프라인) 호출 클라이언트.
 *
 * 기능
 * - 문서 처리(텍스트 추출/청킹/임베딩) 요청을 AI 서버로 전달합니다.
 *
 * 엔드포인트
 * - POST {baseUrl}/internal/ai/rag-documents/ingest
 */
@Component
public class RagAiClient {
    private static final Logger log = LoggerFactory.getLogger(RagAiClient.class);

    private final RestClient restClient;
    /** AI 서버 베이스 URL(예: http://localhost:8000) */
    private final String baseUrl;
    /** 내부 인증 토큰(X-Internal-Token 헤더) */
    private final String internalToken;

    /**
     * 구성값을 주입받아 클라이언트를 초기화합니다.
     *
     * @param baseUrl AI 서버 베이스 URL (예: http://localhost:8000)
     * @param internalToken 내부 호출 토큰(옵션, 없으면 헤더 생략)
     * @param timeoutSeconds HTTP 타임아웃(초)
     */
    public RagAiClient(
        @Value("${app.rag.ai.base-url:http://localhost:8000}") String baseUrl,
        @Value("${app.rag.ai.token:}") String internalToken,
        @Value("${app.rag.ai.timeout-seconds:10}") long timeoutSeconds
    ) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.internalToken = internalToken == null ? "" : internalToken;
        
        // 타임아웃 설정을 위한 RequestFactory 생성
        Duration timeout = Duration.ofSeconds(timeoutSeconds);
        org.springframework.http.client.SimpleClientHttpRequestFactory requestFactory = 
            new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        
        // RestClient 빌더 생성
        RestClient.Builder builder = RestClient.builder()
            .baseUrl(this.baseUrl)
            .requestFactory(requestFactory)
            .defaultRequest(request -> {
                request.headers(headers -> {
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    if (!this.internalToken.isBlank()) {
                        headers.set("X-Internal-Token", this.internalToken);
                    }
                });
            });
        
        this.restClient = builder.build();
    }

    /**
     * 사규 Ingest 요청을 AI 서버로 전송합니다.
     *
     * Backend → AI 서버로 사규 문서 처리 요청을 보냅니다.
     * AI 서버는 처리 완료 후 PATCH /internal/rag/documents/{ragDocumentPk}/status 로 콜백을 보냅니다.
     *
     * @param ragDocumentPk RAG 문서 PK (UUID)
     * @param documentId 사규 문서 ID (예: POL-EDU-015)
     * @param version 문서 버전
     * @param sourceUrl 원본 파일 URL (S3 또는 presigned URL)
     * @param domain 문서 도메인 (기본값: "POLICY")
     * @param department 부서 범위 (전체 부서, 총무팀, 기획팀, 마케팅팀, 인사팀, 재무팀, 개발팀, 영업팀, 법무팀)
     * @return AiResponse(accepted / jobId / status)
     * @throws Exception 네트워크/IO 오류 등
     */
    public AiResponse ingest(
        UUID ragDocumentPk,
        String documentId,
        Integer version,
        String sourceUrl,
        String domain,
        String department
    ) throws Exception {
        // requestId와 traceId 생성 (멱등성 및 로그 상관관계)
        UUID requestId = UUID.randomUUID();
        String traceId = "trace-" + ragDocumentPk.toString().substring(0, 8);

        java.util.HashMap<String, Object> requestBody = new java.util.HashMap<>();
        requestBody.put("ragDocumentPk", ragDocumentPk.toString());
        requestBody.put("documentId", documentId);
        requestBody.put("version", version);
        requestBody.put("sourceUrl", sourceUrl);
        requestBody.put("domain", domain != null && !domain.isBlank() ? domain : "POLICY");
        requestBody.put("requestId", requestId.toString());
        requestBody.put("traceId", traceId);
        if (department != null && !department.isBlank()) {
            requestBody.put("department", department);
        }

        try {
            AiResponse response = restClient.post()
                .uri("/internal/ai/rag-documents/ingest")
                .header("X-Request-Id", requestId.toString())
                .body(requestBody)
                .retrieve()
                .body(AiResponse.class);

            if (response != null) {
                return response;
            } else {
                log.warn("AI ingest response is null, returning default ack");
                return new AiResponse(true, ragDocumentPk.toString(), documentId, version, "PROCESSING", requestId.toString(), traceId);
            }
        } catch (RestClientException e) {
            log.warn("AI ingest call failed: ragDocumentPk={}, documentId={}, error={}",
                ragDocumentPk, documentId, e.getMessage());
            throw new RuntimeException("AI ingest call failed: " + e.getMessage(), e);
        }
    }

    /**
     * @deprecated 이 메서드는 더 이상 사용되지 않습니다. {@link #ingest(UUID, String, Integer, String, String)}를 사용하세요.
     */
    @Deprecated
    public AiResponse process(UUID documentId, String title, String domain, String fileUrl, Instant requestedAt) throws Exception {
        throw new UnsupportedOperationException(
            "process() is deprecated. Please use ingest(ragDocumentPk, documentId, version, sourceUrl, domain) instead."
        );
    }

    /**
     * @deprecated 이 메서드는 잘못 구현되었습니다. 
     * AI → Backend 콜백은 AI 서버에서 호출하는 것이며, 
     * Backend → AI 요청은 {@link #ingest(UUID, String, Integer, String, String)}를 사용하세요.
     */
    @Deprecated
    public void updateDocumentStatus(
        UUID ragDocumentPk,
        String status,
        String processedAt,
        String failReason,
        Integer version,
        String documentId
    ) throws Exception {
        throw new UnsupportedOperationException(
            "updateDocumentStatus is deprecated. " +
            "This should be a callback from AI server to Backend, not a request from Backend to AI. " +
            "Use ingest() for Backend → AI requests."
        );
    }

    /**
     * AI 서버의 Ingest 응답 모델.
     * - received: 요청 수신 여부
     * - ragDocumentPk: RAG 문서 PK
     * - documentId: 문서 ID
     * - version: 문서 버전
     * - status: 초기 상태(예: PROCESSING)
     * - requestId: 요청 ID
     * - traceId: 추적 ID
     */
    public static class AiResponse {
        private boolean received;
        private String ragDocumentPk;
        private String documentId;
        private Integer version;
        private String status;
        private String requestId;
        private String traceId;

        public AiResponse() {}
        public AiResponse(boolean received, String ragDocumentPk, String documentId, Integer version, String status, String requestId, String traceId) {
            this.received = received;
            this.ragDocumentPk = ragDocumentPk;
            this.documentId = documentId;
            this.version = version;
            this.status = status;
            this.requestId = requestId;
            this.traceId = traceId;
        }
        public boolean isReceived() { return received; }
        public String getRagDocumentPk() { return ragDocumentPk; }
        public String getDocumentId() { return documentId; }
        public Integer getVersion() { return version; }
        public String getStatus() { return status; }
        public String getRequestId() { return requestId; }
        public String getTraceId() { return traceId; }
        
        // 하위 호환성을 위한 메서드
        @Deprecated
        public boolean isAccepted() { return received; }
        @Deprecated
        public String getJobId() { return requestId; } // jobId 대신 requestId 반환
    }
}

