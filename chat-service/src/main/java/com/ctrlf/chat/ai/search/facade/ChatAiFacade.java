package com.ctrlf.chat.ai.search.facade;

import com.ctrlf.chat.ai.search.dto.ChatCompletionRequest;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatAiFacade {

    private final WebClient aiWebClient;

    // ✅ NDJSON line-by-line 스트리밍
    public Flux<String> streamChat(ChatCompletionRequest request) {
        log.info("[CHAT → AI/STREAM] start sessionId={}, userId={}",
            request.getSession_id(), request.getUser_id());

        return aiWebClient.post()
            .uri("/ai/chat/stream")
            .header(HttpHeaders.ACCEPT, "application/x-ndjson")
            .bodyValue(request)
            .retrieve()
            .bodyToFlux(DataBuffer.class)
            .flatMap(buffer -> {
                try {
                    String chunk =
                        StandardCharsets.UTF_8
                            .decode(buffer.asByteBuffer())
                            .toString();
                    return Flux.fromArray(chunk.split("\n"));
                } finally {
                    DataBufferUtils.release(buffer);
                }
            })
            .filter(line -> !line.isBlank())
            .doOnError(e -> log.error("[CHAT → AI/STREAM] error", e));
    }
}
