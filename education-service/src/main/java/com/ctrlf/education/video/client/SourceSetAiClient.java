package com.ctrlf.education.video.client;

import com.ctrlf.education.video.client.SourceSetAiDtos.StartRequest;
import com.ctrlf.education.video.client.SourceSetAiDtos.StartResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 소스셋 AI 서버 호출 클라이언트 (RestClient 방식).
 * 
 * <p>기능:
 * <ul>
 *   <li>소스셋 작업 시작 요청 (RAGFlow 적재 + 스크립트 생성)</li>
 * </ul>
 * 
 * <p>엔드포인트:
 * <ul>
 *   <li>POST /internal/ai/source-sets/{sourceSetId}/start - 소스셋 작업 시작</li>
 * </ul>
 */
@Component
public class SourceSetAiClient {

    private static final Logger log = LoggerFactory.getLogger(SourceSetAiClient.class);
    
    private final RestClient restClient;
    private final String baseUrl;
    private final ObjectMapper objectMapper;

    /**
     * RestClient를 구성하여 초기화합니다.
     * 
     * @param baseUrl AI 서버 베이스 URL
     * @param internalToken 내부 인증 토큰(옵션)
     * @param objectMapper JSON 직렬화용 ObjectMapper
     */
    public SourceSetAiClient(
        @Value("${app.video.ai.base-url:http://localhost:8000}") String baseUrl,
        @Value("${app.video.ai.token:}") String internalToken,
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
        
        // 내부 토큰이 있으면 헤더 추가
        if (internalToken != null && !internalToken.isBlank()) {
            builder.defaultHeader("X-Internal-Token", internalToken);
        }
        
        this.restClient = builder.build();
        log.info("SourceSetAiClient 초기화 완료: baseUrl={}", this.baseUrl);
    }

    /**
     * 소스셋 작업 시작 요청.
     * 
     * @param sourceSetId 소스셋 ID
     * @param request 시작 요청 (videoId, scriptJobId, documents 등)
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
            // JSON 문자열을 직접 body로 전송 (RestClient가 객체를 직렬화할 때 문제가 있을 수 있음)
            StartResponse response = restClient.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBodyJson)
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
            log.error("FastAPI 요청 실패: POST {} (sourceSetId={}), error={}", fullUrl, sourceSetId, e.getMessage());
            throw e;
        }
    }
}
