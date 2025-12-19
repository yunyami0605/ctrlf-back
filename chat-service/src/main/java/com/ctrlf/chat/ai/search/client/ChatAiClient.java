package com.ctrlf.chat.ai.search.client;

import com.ctrlf.chat.ai.search.dto.ChatAiMessage;
import com.ctrlf.chat.ai.search.dto.ChatAiRequest;
import com.ctrlf.chat.ai.search.dto.ChatAiResponse;
import com.ctrlf.chat.dto.summary.ChatSessionSummaryMessage;
import com.ctrlf.chat.dto.summary.ChatSessionSummaryRequest;
import com.ctrlf.chat.dto.summary.ChatSessionSummaryResponse;
import com.ctrlf.chat.entity.ChatMessage;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatAiClient {

    private final WebClient aiWebClient;

    // ✅ 기존 채팅 응답용
    public ChatAiResponse ask(
        UUID sessionId,
        UUID userId,
        String userRole,
        String department,
        String domain,
        String channel,
        String message
    ) {
        ChatAiRequest request =
            new ChatAiRequest(
                sessionId,
                userId,
                userRole,
                department,
                domain,
                channel,
                List.of(new ChatAiMessage("user", message))
            );

        return aiWebClient.post()
            .uri("/ai/chat/messages")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(ChatAiResponse.class)
            .block();
    }

    // ✅ 세션 요약 전용
    public ChatSessionSummaryResponse summarizeSession(
        UUID sessionId,
        List<ChatMessage> messages
    ) {
        ChatSessionSummaryRequest request =
            new ChatSessionSummaryRequest(
                sessionId,
                messages.stream()
                    .map(ChatSessionSummaryMessage::from)
                    .toList(),
                150
            );

        log.info("[AI][SUMMARY] request -> {}", request);

        return aiWebClient.post()
            .uri("/ai/chat/session-summary")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(ChatSessionSummaryResponse.class)
            .block();
    }
}
