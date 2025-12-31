package com.ctrlf.common.client;

import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.client.RestClient;

/**
 * AI 서버 호출 시 필수 헤더를 자동으로 추가하는 유틸리티
 * 
 * <p>백엔드에서 AI 서버로 요청할 때 반드시 전달해야 하는 헤더:
 * <ul>
 *   <li>X-Trace-Id (필수) - 요청마다 생성 또는 컨텍스트에서 가져옴</li>
 *   <li>X-User-Id (필수) - JWT에서 자동 추출</li>
 *   <li>X-Dept-Id (필수) - JWT에서 자동 추출</li>
 *   <li>X-Conversation-Id (권장) - 세션/대화 컨텍스트에서 가져옴</li>
 *   <li>X-Turn-Id (권장) - 대화 턴 번호</li>
 * </ul>
 */
public final class AiRestClientHeaders {
    private AiRestClientHeaders() {}

    /**
     * RestClient 요청에 필수 헤더를 추가합니다.
     * 
     * <p>JWT에서 자동으로 추출:
     * <ul>
     *   <li>X-User-Id: JWT의 subject</li>
     *   <li>X-Dept-Id: JWT의 department 클레임</li>
     * </ul>
     * 
     * <p>파라미터로 전달:
     * <ul>
     *   <li>X-Trace-Id: traceId (필수)</li>
     *   <li>X-Conversation-Id: conversationId (선택)</li>
     *   <li>X-Turn-Id: turnId (선택)</li>
     * </ul>
     * 
     * @param requestSpec RestClient 요청 스펙
     * @param traceId Trace ID (필수)
     * @param conversationId Conversation ID (선택, null 가능)
     * @param turnId Turn ID (선택, null 가능)
     * @return 헤더가 추가된 RestClient 요청 스펙
     */
    public static RestClient.RequestHeadersSpec<?> addRequiredHeaders(
        RestClient.RequestHeadersSpec<?> requestSpec,
        UUID traceId,
        String conversationId,
        Integer turnId
    ) {
        // JWT에서 정보 추출
        Jwt jwt = getJwtFromContext();
        String userId = jwt != null ? jwt.getSubject() : "";
        String deptId = extractDeptId(jwt);

        // 필수 헤더 추가
        requestSpec.header("X-Trace-Id", traceId != null ? traceId.toString() : "");
        requestSpec.header("X-User-Id", userId);
        requestSpec.header("X-Dept-Id", deptId);

        // 선택 헤더 추가
        if (conversationId != null && !conversationId.isBlank()) {
            requestSpec.header("X-Conversation-Id", conversationId);
        }

        if (turnId != null) {
            requestSpec.header("X-Turn-Id", turnId.toString());
        }

        return requestSpec;
    }

    /**
     * RestClient 요청에 필수 헤더를 추가합니다 (모든 값 직접 전달).
     * 
     * @param requestSpec RestClient 요청 스펙
     * @param traceId Trace ID (필수)
     * @param userId User ID (필수)
     * @param deptId Department ID (필수)
     * @param conversationId Conversation ID (선택)
     * @param turnId Turn ID (선택)
     * @return 헤더가 추가된 RestClient 요청 스펙
     */
    public static RestClient.RequestHeadersSpec<?> addRequiredHeaders(
        RestClient.RequestHeadersSpec<?> requestSpec,
        UUID traceId,
        String userId,
        String deptId,
        String conversationId,
        Integer turnId
    ) {
        // 필수 헤더 추가
        requestSpec.header("X-Trace-Id", traceId != null ? traceId.toString() : "");
        requestSpec.header("X-User-Id", userId != null ? userId : "");
        requestSpec.header("X-Dept-Id", deptId != null ? deptId : "");

        // 선택 헤더 추가
        if (conversationId != null && !conversationId.isBlank()) {
            requestSpec.header("X-Conversation-Id", conversationId);
        }

        if (turnId != null) {
            requestSpec.header("X-Turn-Id", turnId.toString());
        }

        return requestSpec;
    }

    /**
     * SecurityContext에서 JWT를 가져옵니다.
     * 
     * @return JWT 토큰 (없으면 null)
     */
    private static Jwt getJwtFromContext() {
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
    private static String extractDeptId(Jwt jwt) {
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

