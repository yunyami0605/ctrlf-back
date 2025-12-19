package com.ctrlf.chat.ai.search.facade;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

@Component
@RequiredArgsConstructor
public class ChatAiStreamClient {

    private final WebClient aiWebClient;

    // AI v1: POST /ai/chat/stream (NDJSON)
    public Flux<String> stream(Map<String, Object> body) {
        return aiWebClient.post()
            .uri("/ai/chat/stream")
            .bodyValue(body)
            .retrieve()
            .bodyToFlux(String.class);
    }
}
