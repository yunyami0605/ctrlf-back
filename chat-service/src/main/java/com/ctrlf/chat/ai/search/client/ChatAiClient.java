package com.ctrlf.chat.ai.search.client;

import com.ctrlf.chat.ai.search.dto.ChatAiMessage;
import com.ctrlf.chat.ai.search.dto.ChatAiRequest;
import com.ctrlf.chat.ai.search.dto.ChatAiResponse;
// ⚠️ session-summary 기능 주석 처리로 인해 사용 안 함
// import com.ctrlf.chat.dto.summary.ChatSessionSummaryMessage;
// import com.ctrlf.chat.dto.summary.ChatSessionSummaryRequest;
// import com.ctrlf.chat.dto.summary.ChatSessionSummaryResponse;
// import com.ctrlf.chat.entity.ChatMessage;
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
        String message,
        String model
    ) {
        ChatAiRequest request =
            new ChatAiRequest(
                sessionId,
                userId,
                userRole,
                department,
                domain,
                channel,
                List.of(new ChatAiMessage("user", message)),
                model
            );

        return aiWebClient.post()
            .uri("/ai/chat/messages")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(ChatAiResponse.class)
            .block();
    }

    // ✅ 후방 호환용 (model 없음) - 기본값(openai) 사용
    public ChatAiResponse ask(
        UUID sessionId,
        UUID userId,
        String userRole,
        String department,
        String domain,
        String channel,
        String message
    ) {
        return ask(sessionId, userId, userRole, department, domain, channel, message, null);
    }

    // ⚠️ 세션 요약 전용 (현재 AI 서비스에 해당 엔드포인트가 없어 주석 처리)
    // AI 서비스에는 FAQ 관련 API만 제공되며, session-summary 엔드포인트는 구현되지 않음
    /*
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
    */
}
