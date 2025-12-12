package com.ctrlf.infra.rag.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AI 서버(RAG 파이프라인) 호출 클라이언트.
 *
 * 기능
 * - 문서 처리(텍스트 추출/청킹/임베딩) 요청을 AI 서버로 전달합니다.
 *
 * 엔드포인트
 * - POST {baseUrl}/ai/rag/process
 *
 * 인증/보안
 * - 내부 호출 토큰을 사용하는 경우, X-Internal-Token 헤더를 추가합니다(옵션).
 *
 * 실패 처리 정책
 * - 2xx가 아닌 응답: RuntimeException을 던집니다(상위에서 예외 처리).
 * - 2xx이지만 응답 바디 파싱 실패: 경고 로그 후 기본 Ack(accepted=true, jobId=unknown, status=QUEUED)으로 대체합니다.
 *
 * 구성
 * - app.rag.ai.base-url: 기본 http://localhost:8000
 * - app.rag.ai.token: 내부 토큰(옵션)
 * - app.rag.ai.timeout-seconds: HTTP 타임아웃(초), 기본 10초
 */
@Component
public class RagAiClient {
    /** HTTP 통신 클라이언트 */
    private static final Logger log = LoggerFactory.getLogger(RagAiClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    /** AI 서버 베이스 URL(예: http://localhost:8000) */
    private final String baseUrl;
    /** 내부 인증 토큰(X-Internal-Token 헤더) */
    private final String internalToken;
    /** 요청 타임아웃 */
    private final Duration timeout;

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
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.httpClient = HttpClient.newBuilder().connectTimeout(this.timeout).build();
        this.objectMapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    /**
     * 문서 처리 요청을 AI 서버로 전송합니다.
     *
     * 요청 바디
     * - documentId: 문서 UUID 문자열
     * - title: 문서 제목
     * - domain: 문서 도메인
     * - fileUrl: 원본 파일 URL(S3 등)
     * - requestedAt: ISO-8601 문자열(요청 시각)
     *
     * 성공/실패 규칙
     * - 2xx: 응답 바디를 AiResponse로 파싱(파싱 실패 시 기본 Ack로 대체)
     * - !2xx: RuntimeException 발생
     *
     * @return AiResponse(accepted / jobId / status)
     * @throws Exception 네트워크/IO 오류 등
     */
    public AiResponse process(UUID documentId, String title, String domain, String fileUrl, Instant requestedAt) throws Exception {
        String url = this.baseUrl + "/ai/rag/process";
        Map<String, Object> body = Map.of(
            "documentId", documentId.toString(),
            "title", title,
            "domain", domain,
            "fileUrl", fileUrl,
            "requestedAt", requestedAt.toString()
        );
        byte[] json = objectMapper.writeValueAsBytes(body);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(this.timeout)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofByteArray(json));
        if (!internalToken.isBlank()) {
            builder.header("X-Internal-Token", internalToken);
        }
        HttpRequest request = builder.build();
        HttpResponse<byte[]> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        int code = resp.statusCode();
        if (code >= 200 && code < 300) {
            try {
                return objectMapper.readValue(resp.body(), AiResponse.class);
            } catch (Exception parseEx) {
                log.warn("AI process response parse failed. code={} body={}", code, new String(resp.body()));
                // 응답 파싱 실패 시 기본 ack로 대체
                return new AiResponse(true, "unknown", "QUEUED");
            }
        }
        log.warn("AI process call failed. code={} body={}", code, new String(resp.body()));
        throw new RuntimeException("AI process call failed with status " + code);
    }

    /**
     * AI 서버의 Ack 응답 모델.
     * - accepted: 요청 수락 여부
     * - jobId: 서버 측 작업 식별자
     * - status: 초기 상태(예: QUEUED)
     */
    public static class AiResponse {
        private boolean accepted;
        private String jobId;
        private String status;

        public AiResponse() {}
        public AiResponse(boolean accepted, String jobId, String status) {
            this.accepted = accepted;
            this.jobId = jobId;
            this.status = status;
        }
        public boolean isAccepted() { return accepted; }
        public String getJobId() { return jobId; }
        public String getStatus() { return status; }
    }
}

