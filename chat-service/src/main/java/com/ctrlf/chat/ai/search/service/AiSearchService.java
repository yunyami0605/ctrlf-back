package com.ctrlf.chat.ai.search.service;

import com.ctrlf.chat.ai.search.dto.AiSearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * AI Gateway 검색 API 호출 담당 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiSearchService {

    private final WebClient aiWebClient;

    /**
     * AI 서버에 검색 요청을 보내고 결과를 반환한다
     * 엔드포인트가 없거나 오류 발생 시 빈 결과를 반환한다
     */
    public Mono<AiSearchResponse> search(
        String query,
        String dataset,
        int topK
    ) {
        return aiWebClient.post()
            .uri("/ai/search")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "query", query,
                "dataset", dataset,
                "top_k", topK
            ))
            .retrieve()
            .onStatus(
                status -> status.is4xxClientError() || status.is5xxServerError(),
                response -> {
                    log.warn("AI 검색 API 호출 실패: status={}, query={}, dataset={}", 
                        response.statusCode(), query, dataset);
                    // onStatus는 예외를 던져야 함
                    return response.createException()
                        .flatMap(ex -> Mono.error(new RuntimeException(
                            String.format("AI 검색 API 오류: HTTP %s", response.statusCode()), ex)));
                }
            )
            .bodyToMono(AiSearchResponse.class)
            .timeout(Duration.ofSeconds(15))
            .onErrorResume(WebClientResponseException.class, e -> {
                log.warn("AI 검색 API 호출 중 예외 발생: status={}, query={}, dataset={}, error={}", 
                    e.getStatusCode(), query, dataset, e.getMessage());
                // 예외 발생 시 빈 응답 반환
                AiSearchResponse emptyResponse = new AiSearchResponse();
                emptyResponse.setResults(java.util.Collections.emptyList());
                return Mono.just(emptyResponse);
            })
            .onErrorResume(Exception.class, e -> {
                log.error("AI 검색 API 호출 중 예상치 못한 예외 발생: query={}, dataset={}, error={}", 
                    query, dataset, e.getMessage(), e);
                // 예외 발생 시 빈 응답 반환
                AiSearchResponse emptyResponse = new AiSearchResponse();
                emptyResponse.setResults(java.util.Collections.emptyList());
                return Mono.just(emptyResponse);
            });
    }
}
