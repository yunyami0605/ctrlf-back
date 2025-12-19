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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class ChatStreamService {

    private final ChatAiFacade chatAiFacade;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatSessionRepository chatSessionRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public void stream(UUID messageId, SseEmitter emitter) {
        executor.execute(() -> {
            try {
                // 1) 연결 직후 meta 이벤트
                emitter.send(
                    SseEmitter.event()
                        .name("meta")
                        .data("stream-start:" + messageId)
                );

                // 2) assistant 메시지 조회 (결과 저장 대상)
                ChatMessage assistant =
                    chatMessageRepository.findById(messageId).orElseThrow();

                UUID sessionId = assistant.getSessionId();

                // 3) 세션 정보 조회 (domain, userUuid)
                ChatSession session = chatSessionRepository.findActiveById(sessionId);
                if (session == null) {
                    throw new IllegalArgumentException("세션을 찾을 수 없습니다: " + sessionId);
                }

                // 4) 해당 세션의 가장 최근 user 메시지
                ChatMessage lastUser =
                    chatMessageRepository
                        .findTopBySessionIdAndRoleOrderByCreatedAtDesc(
                            sessionId,
                            "user"
                        )
                        .orElseThrow();

                // 5) AI 스트리밍 요청 생성
                ChatCompletionRequest req =
                    new ChatCompletionRequest(
                        sessionId,
                        session.getUserUuid(),
                        "EMPLOYEE",
                        session.getDomain(),  // department
                        session.getDomain(),  // domain
                        "WEB",
                        List.of(new Message("user", lastUser.getContent()))
                    );

                // 6) AI NDJSON 스트림 구독
                Flux<String> aiStream = chatAiFacade.streamChat(req);

                StringBuilder answerBuf = new StringBuilder();

                aiStream.subscribe(
                    line -> handleLine(line, emitter, answerBuf),
                    error -> handleError(error, emitter),
                    () -> handleComplete(emitter, assistant, answerBuf)
                );

            } catch (Exception e) {
                handleError(e, emitter);
            }
        });
    }

    private void handleLine(
        String line,
        SseEmitter emitter,
        StringBuilder answerBuf
    ) {
        try {
            JsonNode json = objectMapper.readTree(line);
            String type = json.get("type").asText();

            switch (type) {
                case "token" -> {
                    String text = json.get("text").asText();
                    answerBuf.append(text);
                    emitter.send(
                        SseEmitter.event()
                            .name("token")
                            .data(text)
                    );
                }
                case "meta" -> {
                    emitter.send(
                        SseEmitter.event()
                            .name("meta")
                            .data(json.toString())
                    );
                }
                case "done" -> {
                    emitter.send(
                        SseEmitter.event()
                            .name("done")
                            .data("END")
                    );
                    emitter.complete();
                }
                case "error" -> {
                    emitter.send(
                        SseEmitter.event()
                            .name("error")
                            .data(json.toString())
                    );
                    emitter.completeWithError(
                        new RuntimeException("AI error")
                    );
                }
                default -> {
                    emitter.send(
                        SseEmitter.event()
                            .name("token")
                            .data(line)
                    );
                }
            }
        } catch (Exception e) {
            handleError(e, emitter);
        }
    }

    private void handleComplete(
        SseEmitter emitter,
        ChatMessage assistant,
        StringBuilder answerBuf
    ) {
        try {
            // ✅ 최종 답변 저장
            assistant.updateContent(answerBuf.toString());
            chatMessageRepository.save(assistant);

            try {
                emitter.send(
                    SseEmitter.event()
                        .name("done")
                        .data("END")
                );
            } catch (Exception ignored) {}
        } finally {
            emitter.complete();
        }
    }

    private void handleError(Throwable error, SseEmitter emitter) {
        try {
            emitter.send(
                SseEmitter.event()
                    .name("error")
                    .data(error.getMessage())
            );
        } catch (Exception ignored) {
        }
        emitter.completeWithError(error);
    }
}
