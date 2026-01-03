package com.ctrlf.education.video.client;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.ctrlf.education.video.dto.VideoDtos.AiVideoResponse;
import com.ctrlf.education.video.dto.VideoDtos.RenderJobRequest;
import com.ctrlf.education.video.dto.VideoDtos.RenderJobResponse;
import com.ctrlf.education.video.dto.VideoDtos.VideoRetryRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AI 서버(영상 생성 파이프라인) 호출 클라이언트 (RestClient 방식).
 *
 * <p>기능:
 * <ul>
 *   <li>영상 재생성 요청</li>
 *   <li>영상 렌더 생성 요청</li>
 * </ul>
 *
 * <p>엔드포인트:
 * <ul>
 *   <li>POST /ai/video/job/{jobId}/retry - 영상 재생성</li>
 *   <li>POST /internal/ai/render-jobs - 영상 렌더 생성</li>
 * </ul>
 *
 * <p>다음 헤더가 자동으로 추가됩니다:
 * <ul>
 *   <li>X-Internal-Token (환경변수 AI_INTERNAL_TOKEN 또는 app.ai.token 설정값에서 읽음)</li>
 *   <li>X-Trace-Id (자동 생성)</li>
 *   <li>X-User-Id (JWT에서 자동 추출)</li>
 *   <li>X-Dept-Id (JWT에서 자동 추출)</li>
 * </ul>
 */
@Component
public class VideoAiClient {

    private static final Logger log = LoggerFactory.getLogger(VideoAiClient.class);
    
    private final RestClient restClient;
    private final String baseUrl;
    private final ObjectMapper objectMapper;

    /**
     * RestClient를 구성하여 초기화합니다.
     * 
     * @param baseUrl AI 서버 베이스 URL (app.video.ai.base-url 설정값 사용)
     * @param internalToken 내부 인증 토큰 (환경변수 AI_INTERNAL_TOKEN 또는 app.ai.token 설정값에서 읽음)
     * @param objectMapper JSON 직렬화용 ObjectMapper
     */
    public VideoAiClient(
        @Value("${app.video.ai.base-url:http://localhost:8000}") String baseUrl,
        @Value("${AI_INTERNAL_TOKEN:${app.ai.token:}}") String internalToken,
        ObjectMapper objectMapper
    ) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        
        // BufferingClientHttpRequestFactory를 사용하여 Content-Length 헤더가 자동으로 설정되도록 함
        // Spring 6.1+ 부터는 기본적으로 body를 버퍼링하지 않아 Content-Length가 설정되지 않을 수 있음
        BufferingClientHttpRequestFactory requestFactory = 
            new BufferingClientHttpRequestFactory(new SimpleClientHttpRequestFactory());
        
        RestClient.Builder builder = RestClient.builder()
            .baseUrl(this.baseUrl)
            .requestFactory(requestFactory);
        
        // X-Internal-Token 헤더 자동 추가 (설정값이 있으면)
        if (internalToken != null && !internalToken.isBlank()) {
            builder.defaultHeader("X-Internal-Token", internalToken);
            log.info("VideoAiClient 초기화 완료: baseUrl={}, X-Internal-Token 설정됨", this.baseUrl);
        } else {
            log.warn("VideoAiClient 초기화 완료: baseUrl={}, X-Internal-Token이 설정되지 않았습니다. FastAPI 서버에서 401 Unauthorized 에러가 발생할 수 있습니다. 환경변수 AI_INTERNAL_TOKEN 또는 app.ai.token 설정을 확인하세요.", this.baseUrl);
        }
        
        this.restClient = builder.build();
    }

    /**
     * 영상 재생성 요청을 AI 서버로 전송합니다.
     *
     * @param jobId   Job ID
     * @param request 요청 바디 (jobId, scriptId, eduId, retry)
     * @return AI 서버 응답 (jobId, accepted, status)
     * @throws RestClientException 네트워크/서버 오류 시
     */
    public AiVideoResponse retryVideoGeneration(UUID jobId, VideoRetryRequest request) {
        String uri = "/ai/video/job/" + jobId + "/retry";
        String fullUrl = baseUrl + uri;
        
        // 요청 body를 JSON 문자열로 직렬화
        String requestBodyJson;
        try {
            requestBodyJson = objectMapper.writeValueAsString(request);
            log.info("=== FastAPI 요청 (POST {}) ===", fullUrl);
            log.info("jobId: {}", jobId);
            log.debug("요청 Body (JSON): {}", requestBodyJson);
        } catch (Exception e) {
            log.error("요청 body 직렬화 실패: jobId={}, error={}", jobId, e.getMessage());
            throw new RuntimeException("요청 body 직렬화 실패", e);
        }
        
        try {
            // JWT에서 정보 추출
            UUID traceId = UUID.randomUUID();
            Jwt jwt = getJwtFromContext();
            String userId = jwt != null ? jwt.getSubject() : "";
            String deptId = extractDeptId(jwt);
            
            // POST 요청 생성 및 헤더 추가
            byte[] bodyBytes = requestBodyJson.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            AiVideoResponse response = restClient.post()
                .uri(uri)
                .header("X-Trace-Id", traceId.toString())
                .header("X-User-Id", userId)
                .header("X-Dept-Id", deptId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ByteArrayResource(bodyBytes))
                .retrieve()
                .body(AiVideoResponse.class);
            
            log.info("=== FastAPI 응답 성공 (jobId: {}) ===", jobId);
            log.debug("응답 Body: {}", response);
            return response;
        } catch (RestClientException e) {
            log.error("FastAPI 요청 실패: POST {} (jobId={}), error={}", fullUrl, jobId, e.getMessage());
            throw e;
        }
    }

    /**
     * 영상 렌더 생성 요청을 AI 서버로 전송합니다.
     *
     * @param request 요청 바디 (jobId, videoId, scriptId, scriptVersion, renderPolicyId, requestId)
     * @return AI 서버 응답 (received, jobId, status)
     * @throws RestClientException 네트워크/서버 오류 시
     */
    public RenderJobResponse createRenderJob(RenderJobRequest request) {
        String uri = "/internal/ai/render-jobs";
        String fullUrl = baseUrl + uri;
        
        // 요청 body를 JSON 문자열로 직렬화
        String requestBodyJson;
        try {
            requestBodyJson = objectMapper.writeValueAsString(request);
            log.info("=== FastAPI 요청 (POST {}) ===", fullUrl);
            log.debug("요청 Body (JSON): {}", requestBodyJson);
        } catch (Exception e) {
            log.error("요청 body 직렬화 실패: error={}", e.getMessage());
            throw new RuntimeException("요청 body 직렬화 실패", e);
        }
        
        try {
            // JWT에서 정보 추출
            UUID traceId = UUID.randomUUID();
            Jwt jwt = getJwtFromContext();
            String userId = jwt != null ? jwt.getSubject() : "";
            String deptId = extractDeptId(jwt);
            
            // POST 요청 생성 및 헤더 추가
            byte[] bodyBytes = requestBodyJson.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            RenderJobResponse response = restClient.post()
                .uri(uri)
                .header("X-Trace-Id", traceId.toString())
                .header("X-User-Id", userId)
                .header("X-Dept-Id", deptId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ByteArrayResource(bodyBytes))
                .retrieve()
                .body(RenderJobResponse.class);
            
            log.info("=== FastAPI 응답 성공 ===");
            log.debug("응답 Body: {}", response);
            return response;
        } catch (RestClientException e) {
            log.error("FastAPI 요청 실패: POST {} (jobId={}), error={}", fullUrl, 
                request != null ? request.jobId() : "unknown", e.getMessage());
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
        if (jwt == null) return "";

        Object deptClaim = jwt.getClaim("department");
        if (deptClaim == null) return "";

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
