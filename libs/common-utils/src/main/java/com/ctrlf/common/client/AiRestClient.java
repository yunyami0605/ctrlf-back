package com.ctrlf.common.client;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * AI 서버 호출용 공통 RestClient
 * 
 * <p>MSA 환경에서 모든 서비스에서 공통으로 사용하는 AI 서버 호출 함수입니다.</p>
 * <p>자동으로 다음 헤더를 추가합니다:
 * <ul>
 *   <li>X-Internal-Token (설정값에서 읽음, app.ai.token)</li>
 *   <li>X-Trace-Id (자동 생성 또는 전달)</li>
 *   <li>X-User-Id (JWT에서 자동 추출)</li>
 *   <li>X-Dept-Id (JWT에서 자동 추출)</li>
 *   <li>X-Conversation-Id (선택, 전달 시 추가)</li>
 *   <li>X-Turn-Id (선택, 전달 시 추가)</li>
 * </ul>
 * 
 * <p>사용 예시:
 * <pre>{@code
 * // 1. 주입
 * @Autowired
 * private AiRestClient aiRestClient;
 * 
 * // 2. 호출 (모든 헤더 자동 추가)
 * Response response = aiRestClient.post(
 *     "/ai/faq/generate",
 *     request,
 *     Response.class
 * );
 * 
 * // 3. conversationId, turnId 포함
 * Response response = aiRestClient.post(
 *     "/ai/chat/messages",
 *     request,
 *     Response.class,
 *     conversationId,
 *     turnId
 * );
 * }</pre>
 */
@Component
public class AiRestClient {

    private final RestClient restClient;

    @Value("${app.ai.token:}")
    private String internalToken;

    /**
     * AiRestClientBuilder를 사용하여 초기화합니다.
     * 
     * @param aiRestClientBuilder RestClient 빌더
     * @param baseUrl AI 서버 베이스 URL
     */
    public AiRestClient(
        AiRestClientBuilder aiRestClientBuilder,
        @Value("${app.ai.base-url:http://localhost:8000}") String baseUrl
    ) {
        this.restClient = aiRestClientBuilder.build(baseUrl);
    }

    /**
     * POST 요청을 보냅니다 (모든 필수 헤더 자동 추가).
     * 
     * @param uri 요청 URI
     * @param body 요청 바디
     * @param responseType 응답 타입
     * @return 응답 객체
     */
    public <T> T post(String uri, Object body, Class<T> responseType) {
        return post(uri, body, responseType, null, null);
    }

    /**
     * POST 요청을 보냅니다 (conversationId, turnId 포함).
     * 
     * @param uri 요청 URI
     * @param body 요청 바디
     * @param responseType 응답 타입
     * @param conversationId Conversation ID (선택)
     * @param turnId Turn ID (선택)
     * @return 응답 객체
     */
    public <T> T post(
        String uri,
        Object body,
        Class<T> responseType,
        String conversationId,
        Integer turnId
    ) {
        UUID traceId = UUID.randomUUID(); // 자동 생성
        
        // JWT에서 정보 추출
        Jwt jwt = getJwtFromContext();
        String userId = jwt != null ? jwt.getSubject() : "";
        String deptId = extractDeptId(jwt);
        
        // POST 요청 생성 및 헤더 추가
        RestClient.RequestBodySpec requestSpec = restClient.post().uri(uri);
        
        // 필수 헤더 추가
        requestSpec.header("X-Trace-Id", traceId.toString());
        requestSpec.header("X-User-Id", userId);
        requestSpec.header("X-Dept-Id", deptId);
        
        // 선택 헤더 추가
        if (conversationId != null && !conversationId.isBlank()) {
            requestSpec.header("X-Conversation-Id", conversationId);
        }
        if (turnId != null) {
            requestSpec.header("X-Turn-Id", turnId.toString());
        }
        
        return requestSpec
            .body(body)
            .retrieve()
            .body(responseType);
    }

    /**
     * GET 요청을 보냅니다 (모든 필수 헤더 자동 추가).
     * 
     * @param uri 요청 URI
     * @param responseType 응답 타입
     * @return 응답 객체
     */
    public <T> T get(String uri, Class<T> responseType) {
        return get(uri, responseType, null, null);
    }

    /**
     * GET 요청을 보냅니다 (conversationId, turnId 포함).
     * 
     * @param uri 요청 URI
     * @param responseType 응답 타입
     * @param conversationId Conversation ID (선택)
     * @param turnId Turn ID (선택)
     * @return 응답 객체
     */
    public <T> T get(
        String uri,
        Class<T> responseType,
        String conversationId,
        Integer turnId
    ) {
        UUID traceId = UUID.randomUUID(); // 자동 생성
        
        // JWT에서 정보 추출
        Jwt jwt = getJwtFromContext();
        String userId = jwt != null ? jwt.getSubject() : "";
        String deptId = extractDeptId(jwt);
        
        // GET 요청 생성 및 헤더 추가
        RestClient.RequestHeadersSpec<?> requestSpec = restClient.get().uri(uri);
        
        // 필수 헤더 추가
        requestSpec.header("X-Trace-Id", traceId.toString());
        requestSpec.header("X-User-Id", userId);
        requestSpec.header("X-Dept-Id", deptId);
        
        // 선택 헤더 추가
        if (conversationId != null && !conversationId.isBlank()) {
            requestSpec.header("X-Conversation-Id", conversationId);
        }
        if (turnId != null) {
            requestSpec.header("X-Turn-Id", turnId.toString());
        }
        
        return requestSpec
            .retrieve()
            .body(responseType);
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

