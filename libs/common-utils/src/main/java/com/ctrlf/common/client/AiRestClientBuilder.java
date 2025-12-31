package com.ctrlf.common.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * AI 서버 호출용 공통 RestClient 빌더
 * 
 * <p>MSA 환경에서 모든 서비스에서 공통으로 사용하는 AI 서버 호출용 RestClient를 생성합니다.</p>
 * <p>자동으로 다음 헤더를 추가합니다:
 * <ul>
 *   <li>X-Internal-Token (설정값에서 읽음, app.ai.token)</li>
 * </ul>
 * 
 * <p>요청 시 RestClientAiHeaders.addHeaders()로 추가로 필요한 헤더를 설정:
 * <ul>
 *   <li>X-Trace-Id (필수)</li>
 *   <li>X-User-Id (필수)</li>
 *   <li>X-Dept-Id (필수)</li>
 *   <li>X-Conversation-Id (권장)</li>
 *   <li>X-Turn-Id (권장)</li>
 * </ul>
 * 
 * <p>사용 예시:
 * <pre>{@code
 * // 1. 빌더 주입
 * @Autowired
 * private AiRestClientBuilder aiRestClientBuilder;
 * 
 * // 2. RestClient 생성
 * RestClient restClient = aiRestClientBuilder.build("http://localhost:8000");
 * 
 * // 3. 요청 시 헤더 추가
 * RestClientAiHeaders.addHeaders(
 *     restClient.post().uri("/ai/chat/messages"),
 *     traceId, userId, deptId, conversationId, turnId, null
 * )
 * .body(request)
 * .retrieve()
 * .body(Response.class);
 * }</pre>
 */
@Component
public class AiRestClientBuilder {

    @Value("${app.ai.token:}")
    private String internalToken;

    /**
     * AI 서버 호출용 RestClient를 생성합니다.
     * 
     * @param baseUrl AI 서버 베이스 URL
     * @return 설정된 RestClient 인스턴스 (X-Internal-Token 헤더 자동 추가됨)
     */
    public RestClient build(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl은 필수입니다.");
        }

        String normalizedUrl = baseUrl.endsWith("/")
            ? baseUrl.substring(0, baseUrl.length() - 1)
            : baseUrl;

        RestClient.Builder builder = RestClient.builder()
            .baseUrl(normalizedUrl);

        // X-Internal-Token 헤더 자동 추가 (설정값이 있으면)
        if (internalToken != null && !internalToken.isBlank()) {
            builder.defaultHeader("X-Internal-Token", internalToken);
        }

        return builder.build();
    }
}

