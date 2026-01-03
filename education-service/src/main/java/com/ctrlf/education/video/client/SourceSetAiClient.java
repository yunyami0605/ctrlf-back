package com.ctrlf.education.video.client;

import com.ctrlf.education.video.client.SourceSetAiDtos.StartRequest;
import com.ctrlf.education.video.client.SourceSetAiDtos.StartResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * 소스셋 AI 서버 호출 클라이언트 (AiRestClientBuilder 사용).
 * 
 * <p>
 * 기능:
 * <ul>
 * <li>소스셋 작업 시작 요청 (RAGFlow 적재 + 스크립트 생성)</li>
 * </ul>
 * 
 * <p>
 * 엔드포인트:
 * <ul>
 * <li>POST /internal/ai/source-sets/{sourceSetId}/start - 소스셋 작업 시작</li>
 * </ul>
 * 
 * <p>
 * 다음 헤더가 자동으로 추가됩니다:
 * <ul>
 * <li>X-Internal-Token (환경변수 AI_INTERNAL_TOKEN 또는 app.ai.token 설정값에서 읽음)</li>
 * <li>X-Trace-Id (자동 생성)</li>
 * <li>X-User-Id (JWT에서 자동 추출)</li>
 * <li>X-Dept-Id (JWT에서 자동 추출)</li>
 * </ul>
 */
@Component
public class SourceSetAiClient {

    private static final Logger log = LoggerFactory.getLogger(SourceSetAiClient.class);

    private final RestClient restClient;
    private final String baseUrl;
    private final ObjectMapper objectMapper;
    private final String internalToken;

    /**
     * AiRestClientBuilder를 사용하여 초기화합니다.
     * 
     * @param baseUrl       AI 서버 베이스 URL (app.video.ai.base-url 설정값 사용)
     * @param internalToken 내부 인증 토큰 (환경변수 AI_INTERNAL_TOKEN 또는 app.ai.token 설정값에서
     *                      읽음)
     * @param objectMapper  JSON 직렬화용 ObjectMapper
     */
    public SourceSetAiClient(
            @Value("${app.video.ai.base-url:http://localhost:8000}") String baseUrl,
            @Value("${AI_INTERNAL_TOKEN:${app.ai.token:}}") String internalToken,
            ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;

        // BufferingClientHttpRequestFactory를 사용하여 Content-Length 헤더가 자동으로 설정되도록 함
        // Spring 6.1+ 부터는 기본적으로 body를 버퍼링하지 않아 Content-Length가 설정되지 않을 수 있음
        BufferingClientHttpRequestFactory requestFactory = new BufferingClientHttpRequestFactory(
                new SimpleClientHttpRequestFactory());

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(this.baseUrl)
                .requestFactory(requestFactory);

        // X-Internal-Token 헤더 자동 추가 (설정값이 있으면)
        if (internalToken != null && !internalToken.isBlank()) {
            builder.defaultHeader("X-Internal-Token", internalToken);
            // 토큰 값의 일부만 마스킹해서 로그 출력 (디버깅용)
            String maskedToken = internalToken.length() > 8
                    ? internalToken.substring(0, 4) + "****" + internalToken.substring(internalToken.length() - 4)
                    : "****";
            log.info("SourceSetAiClient 초기화 완료: baseUrl={}, X-Internal-Token 설정됨 (토큰: {}, 길이: {})",
                    this.baseUrl, maskedToken, internalToken.length());
        } else {
            log.warn(
                    "SourceSetAiClient 초기화 완료: baseUrl={}, X-Internal-Token이 설정되지 않았습니다. FastAPI 서버에서 401 Unauthorized 에러가 발생할 수 있습니다. 환경변수 AI_INTERNAL_TOKEN 또는 app.ai.token 설정을 확인하세요.",
                    this.baseUrl);
        }

        this.restClient = builder.build();
        this.internalToken = internalToken;
    }

    /**
     * 소스셋 작업 시작 요청.
     * 
     * @param sourceSetId 소스셋 ID
     * @param request     시작 요청 (videoId, scriptJobId, documents 등)
     * @return 시작 응답 (received, sourceSetId, scriptJobId, status)
     * @throws RestClientException 네트워크/서버 오류 시
     */
    public StartResponse startSourceSet(String sourceSetId, StartRequest request) {
        String uri = "/internal/ai/source-sets/" + sourceSetId + "/start";
        String fullUrl = baseUrl + uri;

        // 요청 body를 JSON 문자열로 직렬화 (null 필드 포함 보장)
        String requestBodyJson;
        try {
            requestBodyJson = objectMapper.writeValueAsString(request);

            // 로그 출력 (예쁘게 포맷팅)
            String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(request);
            log.info("=== FastAPI 요청 (POST {}) ===", fullUrl);
            log.info("sourceSetId: {}", sourceSetId);
            log.info("요청 Body (JSON):\n{}", prettyJson);
        } catch (JsonProcessingException e) {
            log.error("요청 body 직렬화 실패: sourceSetId={}, error={}", sourceSetId, e.getMessage());
            throw new RuntimeException("요청 body 직렬화 실패", e);
        }

        try {
            // JWT에서 정보 추출
            UUID traceId = UUID.randomUUID();
            Jwt jwt = getJwtFromContext();
            String userId = jwt != null ? jwt.getSubject() : "";
            String deptId = extractDeptId(jwt);

            // POST 요청 생성 및 헤더 추가
            // body를 ByteArrayResource로 감싸서 Content-Length가 올바르게 설정되도록 함
            byte[] bodyBytes = requestBodyJson.getBytes(java.nio.charset.StandardCharsets.UTF_8);

            // 요청 헤더 상세 로그 출력
            String maskedToken = internalToken != null && internalToken.length() > 8
                    ? internalToken.substring(0, 4) + "****" + internalToken.substring(internalToken.length() - 4)
                    : (internalToken != null ? "****" : "null");
            log.info("=== FastAPI 요청 헤더 ===");
            log.info("URL: POST {}", fullUrl);
            log.info("X-Internal-Token: {} (길이: {})", maskedToken, internalToken != null ? internalToken.length() : 0);
            log.info("X-Trace-Id: {}", traceId);
            log.info("X-User-Id: {}", userId);
            log.info("X-Dept-Id: {}", deptId);
            log.info("Content-Type: {}", MediaType.APPLICATION_JSON);
            log.info("Content-Length: {}", bodyBytes.length);

            StartResponse response = restClient.post()
                    .uri(uri)
                    .header("X-Trace-Id", traceId.toString())
                    .header("X-User-Id", userId)
                    .header("X-Dept-Id", deptId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ByteArrayResource(bodyBytes))
                    .retrieve()
                    .body(StartResponse.class);

            // 응답 body를 JSON으로 직렬화하여 로그 출력 (예쁘게 포맷팅)
            try {
                String responseBodyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response);
                log.info("=== FastAPI 응답 성공 (sourceSetId: {}) ===", sourceSetId);
                log.info("응답 Body (JSON):\n{}", responseBodyJson);
            } catch (Exception e) {
                log.warn("응답 body 직렬화 실패: sourceSetId={}, error={}", sourceSetId, e.getMessage());
                // 포맷팅 실패 시 한 줄로 출력
                try {
                    String responseBodyJson = objectMapper.writeValueAsString(response);
                    log.info("FastAPI 응답 성공: sourceSetId={}, responseBody={}", sourceSetId, responseBodyJson);
                } catch (Exception e2) {
                    log.info("FastAPI 응답 성공: sourceSetId={}, response={}", sourceSetId, response);
                }
            }
            return response;
        } catch (RestClientException e) {
            log.error("=== FastAPI 요청 실패 ===");
            log.error("URL: POST {}", fullUrl);
            log.error("sourceSetId: {}", sourceSetId);
            log.error("에러 메시지: {}", e.getMessage());

            // 전송된 헤더 정보 재출력 (디버깅용)
            String maskedToken = internalToken != null && internalToken.length() > 8
                    ? internalToken.substring(0, 4) + "****" + internalToken.substring(internalToken.length() - 4)
                    : (internalToken != null ? "****" : "null");
            log.error("전송된 X-Internal-Token: {} (길이: {})", maskedToken,
                    internalToken != null ? internalToken.length() : 0);

            if (e.getMessage() != null && e.getMessage().contains("INVALID_TOKEN")) {
                log.error(
                        "토큰 인증 실패: FastAPI 서버의 BACKEND_INTERNAL_TOKEN과 education-service의 AI_INTERNAL_TOKEN(또는 app.ai.token) 값이 일치하는지 확인하세요.");
                log.error("현재 전송된 토큰: {} (길이: {})", maskedToken, internalToken != null ? internalToken.length() : 0);
            }
            throw e;
        }
    }

    /**
     * SecurityContext에서 JWT를 가져옵니다.
     * 
     * @return JWT 토큰 (없으면 null)
     */
    private Jwt getJwtFromContext() {
        try {
            Object principal = SecurityContextHolder.getContext()
                    .getAuthentication()
                    .getPrincipal();

            if (principal instanceof Jwt) {
                return (Jwt) principal;
            }
        } catch (Exception e) {
            // SecurityContext가 없거나 JWT가 없으면 null 반환
        }
        return null;
    }

    /**
     * JWT에서 부서 ID를 추출합니다.
     * 
     * @param jwt JWT 토큰
     * @return 부서 ID (없으면 빈 문자열)
     */
    private String extractDeptId(Jwt jwt) {
        if (jwt == null)
            return "";

        Object deptClaim = jwt.getClaim("department");
        if (deptClaim == null)
            return "";

        if (deptClaim instanceof String) {
            return (String) deptClaim;
        }

        if (deptClaim instanceof java.util.List) {
            @SuppressWarnings("unchecked")
            java.util.List<String> deptList = (java.util.List<String>) deptClaim;
            return deptList.isEmpty() ? "" : deptList.get(0);
        }

        return "";
    }
}
