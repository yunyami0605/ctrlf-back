package com.ctrlf.chat.ai.search.service;

import com.ctrlf.chat.ai.search.dto.AiSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * AI Gateway 검색 API 호출 담당 서비스
 */
@Service
@RequiredArgsConstructor
public class AiSearchService {

    private final WebClient aiWebClient;

    /**
     * AI 서버에 검색 요청을 보내고 결과를 반환한다
     */
    public Mono<AiSearchResponse> search(
        String query,
        String dataset,
        int topK
    ) {
        return aiWebClient.post()
            .uri("/search")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "query", query,
                "dataset", dataset,
                "top_k", topK
            ))
            .retrieve()
            .bodyToMono(AiSearchResponse.class)
            .timeout(Duration.ofSeconds(15));
    }
}
