package com.ctrlf.education.quiz.client;

import com.ctrlf.education.quiz.client.QuizAiDtos.GenerateRequest;
import com.ctrlf.education.quiz.client.QuizAiDtos.GenerateResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

/**
 * 퀴즈 AI 서버 호출 클라이언트 (RestClient 방식).
 */
@Component
public class QuizAiClient {

    private static final Logger log = LoggerFactory.getLogger(QuizAiClient.class);

    private final RestClient restClient;

    /**
     * RestClient를 구성하여 초기화
     * 
     * @param internalToken 내부 인증 토큰(옵션)
     */
    public QuizAiClient(
        @Value("${app.quiz.ai.base-url:http://localhost:8000}") String baseUrl,
        @Value("${app.quiz.ai.token:}") String internalToken
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(30000);  // 연결 타임아웃: 30초
        requestFactory.setReadTimeout(600000);     // 읽기 타임아웃: 600초(10분) - 퀴즈 생성은 LLM 호출로 시간이 걸림
        
        RestClient.Builder builder = RestClient.builder()
            .baseUrl(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl)
            .requestFactory(requestFactory);
        
        // 내부 토큰이 있으면 헤더 추가
        if (internalToken != null && !internalToken.isBlank()) {
            builder.defaultHeader("X-Internal-Token", internalToken);
        }
        
        this.restClient = builder.build();
    }

    /**
     * 퀴즈 문항 자동 생성 요청.
     * 
     * @param request 생성 요청 (language, numQuestions, quizCandidateBlocks 등)
     * @return 생성된 문항 목록
     * @throws RestClientException 네트워크/서버 오류 시
     */
    public GenerateResponse generate(GenerateRequest request) {
        try {
            log.info("AI 퀴즈 생성 요청: language={}, numQuestions={}, quizCandidateBlocksCount={}",
                request.getLanguage(), request.getNumQuestions(),
                request.getQuizCandidateBlocks() != null ? request.getQuizCandidateBlocks().size() : 0);

            GenerateResponse response = restClient.post()
                .uri("/ai/quiz/generate")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), (req, res) -> {
                    String errorBody = "";
                    try {
                        errorBody = res.getBody() != null ? res.getBody().toString() : "";
                    } catch (Exception e) {}
                    
                    log.error("AI 퀴즈 생성 실패: status={}, language={}, numQuestions={}, errorBody={}",
                        res.getStatusCode(), request.getLanguage(), request.getNumQuestions(), errorBody);
                    throw new RestClientException(
                        String.format("AI 서비스 오류: HTTP %s - %s", res.getStatusCode(), errorBody)
                    );
                })
                .body(GenerateResponse.class);

            log.info("AI 퀴즈 생성 응답: generatedCount={}, questionsCount={}",
                response != null ? response.getGeneratedCount() : null,
                response != null && response.getQuestions() != null ? response.getQuestions().size() : 0);

            return response;
        } catch (RestClientException e) {
            log.error("AI 퀴즈 생성 요청 실패: language={}, numQuestions={}, error={}",
                request.getLanguage(), request.getNumQuestions(), e.getMessage(), e);
            throw e;
        }
    }
}
