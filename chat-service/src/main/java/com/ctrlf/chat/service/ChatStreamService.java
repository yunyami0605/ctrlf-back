package com.ctrlf.chat.service;

import com.ctrlf.chat.ai.search.dto.ChatCompletionRequest;
import com.ctrlf.chat.ai.search.dto.ChatCompletionRequest.Message;
import com.ctrlf.chat.ai.search.facade.ChatAiFacade;
import com.ctrlf.chat.entity.ChatMessage;
import com.ctrlf.chat.entity.ChatSession;
import com.ctrlf.chat.repository.ChatMessageRepository;
import com.ctrlf.chat.repository.ChatSessionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatStreamService {

    private final ChatAiFacade chatAiFacade;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SseEmitter stream(UUID messageId) {
        SseEmitter emitter = new SseEmitter(0L); // 무제한

        try {
            ChatMessage assistant =
                chatMessageRepository.findById(messageId).orElseThrow();

            UUID sessionId = assistant.getSessionId();
            ChatSession session =
                chatSessionRepository.findActiveById(sessionId);

            if (session == null) {
                throw new IllegalArgumentException("세션 없음: " + sessionId);
            }

            ChatMessage lastUser =
                chatMessageRepository
                    .findTopBySessionIdAndRoleOrderByCreatedAtDesc(
                        sessionId, "user")
                    .orElseThrow();

            ChatCompletionRequest req =
                new ChatCompletionRequest(
                    "stream-" + messageId,
                    sessionId,
                    session.getUserUuid(),
                    "EMPLOYEE",
                    session.getDomain(),
                    session.getDomain(),
                    "WEB",
                    List.of(new Message("user", lastUser.getContent()))
                );

            StringBuilder answerBuf = new StringBuilder();

            // 🔹 스트림 시작 알림
            emitter.send(SseEmitter.event()
                .name("meta")
                .data("stream-start"));

            Disposable subscription =
                chatAiFacade.streamChat(req).subscribe(
                    line -> handleLine(line, emitter, answerBuf),
                    error -> handleStreamError(error, emitter),
                    () -> handleComplete(emitter, assistant, answerBuf)
                );

            emitter.onCompletion(subscription::dispose);
            emitter.onTimeout(subscription::dispose);

        } catch (Exception e) {
            handleStreamError(e, emitter);
        }

        return emitter;
    }

    private void handleLine(
        String line,
        SseEmitter emitter,
        StringBuilder answerBuf
    ) {
        try {
            JsonNode json = objectMapper.readTree(line);
            String type = json.path("type").asText();

            if ("token".equals(type)) {
                String text = json.path("text").asText();
                answerBuf.append(text);
                emitter.send(
                    SseEmitter.event()
                        .name("token")
                        .data(text)
                );
            }

            // ❗ meta / done / error 는 여기서 처리하지 않음

        } catch (Exception e) {
            // JSON 파싱 실패 → 무시 (NDJSON chunk 경계 보호)
            log.debug("skip non-json line: {}", line);
        }
    }

    private void handleComplete(
        SseEmitter emitter,
        ChatMessage assistant,
        StringBuilder answerBuf
    ) {
        try {
            assistant.updateContent(answerBuf.toString());
            chatMessageRepository.save(assistant);

            emitter.send(
                SseEmitter.event()
                    .name("done")
                    .data("END")
            );
        } catch (Exception e) {
            log.error("final save error", e);
        } finally {
            emitter.complete();
        }
    }

    private void handleStreamError(Throwable error, SseEmitter emitter) {
        try {
            emitter.send(
                SseEmitter.event()
                    .name("error")
                    .data(error.getMessage())
            );
        } catch (Exception ignored) {}
        emitter.complete();
    }
}
