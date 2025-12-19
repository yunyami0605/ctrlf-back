package com.ctrlf.chat.ai.search.facade;

import com.ctrlf.chat.ai.search.dto.ChatCompletionRequest;
import com.ctrlf.chat.ai.search.dto.ChatCompletionRequest.Message;
import com.ctrlf.chat.ai.search.dto.ChatCompletionResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatAiFacade {

    private final WebClient aiWebClient;

    public ChatCompletionResponse chat(
        UUID sessionId,
        UUID userId,
        String domain,
        String userMessage
    ) {
        ChatCompletionRequest request =
            new ChatCompletionRequest(
                sessionId,
                userId,
                "EMPLOYEE",
                domain,
                domain,
                "WEB",
                List.of(new Message("user", userMessage))
            );

        ChatCompletionResponse response = aiWebClient.post()
            .uri("/ai/chat/messages")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(ChatCompletionResponse.class)
            .block();

        return response;
    }

    // ✅ 스트리밍 호출
    public Flux<String> streamChat(ChatCompletionRequest request) {
        log.info("[CHAT → AI/STREAM] start");
        log.info("[CHAT → AI/STREAM] request: session_id={}, user_id={}, domain={}", 
            request.getSession_id(), request.getUser_id(), request.getDomain());

        return aiWebClient.post()
            .uri("/ai/chat/stream")
            .bodyValue(request)
            .retrieve()
            .onStatus(
                status -> status.is4xxClientError() || status.is5xxServerError(),
                response -> {
                    log.error("[CHAT → AI/STREAM] error: status={}", response.statusCode());
                    return response.bodyToMono(String.class)
                        .doOnNext(body -> log.error("[CHAT → AI/STREAM] error body: {}", body))
                        .then(Mono.error(new RuntimeException("AI stream error: " + response.statusCode())));
                }
            )
            .bodyToFlux(String.class)
            .doOnError(error -> log.error("[CHAT → AI/STREAM] flux error", error));
    }
}
