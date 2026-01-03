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
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    // AI 최대 지연 시간(61초) + 여유 시간을 고려하여 180초로 설정
    private static final long SSE_TIMEOUT_MS = 180_000L;

    public SseEmitter stream(UUID messageId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

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

            // TODO: 스트리밍에서 A/B 테스트 지원을 위해서는
            // user 메시지 저장 시 model 정보를 함께 저장하고 여기서 조회해야 함
            // 현재는 기본값(null = openai) 사용
            ChatCompletionRequest req =
                new ChatCompletionRequest(
                    "stream-" + messageId,
                    sessionId,
                    session.getUserUuid(),
                    "EMPLOYEE",
                    session.getDomain(),
                    session.getDomain(),
                    "WEB",
                    List.of(new Message("user", lastUser.getContent())),
                    null  // A/B 테스트 model (현재 스트리밍에서는 기본값 사용)
                );

            StringBuilder answerBuf = new StringBuilder();
            StreamContext context = new StreamContext(emitter, assistant, answerBuf);

            Disposable subscription =
                chatAiFacade.streamChat(req).subscribe(
                    line -> handleLine(line, context),
                    error -> handleStreamError(error, context),
                    () -> {
                        // AI 서버가 done 이벤트를 보내지 않고 스트림이 완료된 경우
                        // (비정상 종료 등)에만 호출됨
                        if (!context.isDoneReceived) {
                            handleComplete(context);
                        }
                    }
                );

            emitter.onCompletion(() -> {
                subscription.dispose();
                log.debug("SSE emitter completed for messageId={}", messageId);
            });
            emitter.onTimeout(() -> {
                subscription.dispose();
                log.warn("SSE emitter timeout for messageId={}", messageId);
            });

        } catch (Exception e) {
            // 초기화 중 에러 발생 시 context가 없으므로 임시 생성
            ChatMessage tempAssistant = null;
            try {
                tempAssistant = chatMessageRepository.findById(messageId).orElse(null);
            } catch (Exception ignored) {}
            
            StreamContext errorContext = new StreamContext(
                emitter,
                tempAssistant != null ? tempAssistant : new ChatMessage(),
                new StringBuilder()
            );
            handleStreamError(e, errorContext);
        }

        return emitter;
    }

    private void handleLine(String line, StreamContext context) {
        try {
            JsonNode json = objectMapper.readTree(line);
            String type = json.path("type").asText();

            switch (type) {
                case "meta":
                    handleMetaEvent(json, context);
                    break;
                case "token":
                    handleTokenEvent(json, context);
                    break;
                case "done":
                    handleDoneEvent(json, context);
                    break;
                case "error":
                    handleErrorEvent(json, context);
                    break;
                default:
                    log.debug("Unknown event type: {}", type);
            }
        } catch (Exception e) {
            // JSON 파싱 실패 → 무시 (NDJSON chunk 경계 보호)
            log.debug("skip non-json line: {}", line);
        }
    }

    /**
     * meta 이벤트 처리
     * AI 서버의 model 정보를 SSE로 전달합니다.
     */
    private void handleMetaEvent(JsonNode json, StreamContext context) {
        try {
            // AI 서버의 meta 이벤트를 그대로 SSE로 전달
            String metaJson = objectMapper.writeValueAsString(json);
            safeSend(context.emitter, SseEmitter.event()
                .name("meta")
                .data(metaJson));
            
            // model 정보를 컨텍스트에 저장 (나중에 메시지 저장 시 사용)
            if (json.has("model")) {
                context.model = json.path("model").asText();
            }
            
            log.debug("Received meta event: model={}", context.model);
        } catch (Exception e) {
            log.warn("Failed to process meta event", e);
        }
    }

    /**
     * token 이벤트 처리
     * 토큰 텍스트를 누적하고 SSE로 전달합니다.
     */
    private void handleTokenEvent(JsonNode json, StreamContext context) {
        try {
            String text = json.path("text").asText();
            context.answerBuf.append(text);
            safeSend(context.emitter, SseEmitter.event()
                .name("token")
                .data(text));
        } catch (Exception e) {
            log.warn("Failed to process token event", e);
        }
    }

    /**
     * done 이벤트 처리
     * AI 서버의 메트릭 정보를 SSE로 전달하고 메시지를 저장합니다.
     */
    private void handleDoneEvent(JsonNode json, StreamContext context) {
        try {
            // AI 서버의 done 이벤트를 그대로 SSE로 전달
            String doneJson = objectMapper.writeValueAsString(json);
            safeSend(context.emitter, SseEmitter.event()
                .name("done")
                .data(doneJson));
            
            context.isDoneReceived = true;
            
            // 메시지 저장 (메트릭 정보 포함)
            saveMessageWithMetrics(json, context);
            
            log.debug("Received done event: total_tokens={}, elapsed_ms={}",
                json.path("total_tokens").asInt(-1),
                json.path("elapsed_ms").asInt(-1));
        } catch (Exception e) {
            log.error("Failed to process done event", e);
        } finally {
            safeComplete(context.emitter);
        }
    }

    /**
     * error 이벤트 처리
     * AI 서버의 에러 정보를 SSE로 전달합니다.
     */
    private void handleErrorEvent(JsonNode json, StreamContext context) {
        try {
            // AI 서버의 error 이벤트를 그대로 SSE로 전달
            String errorJson = objectMapper.writeValueAsString(json);
            safeSend(context.emitter, SseEmitter.event()
                .name("error")
                .data(errorJson));
            
            context.isDoneReceived = true;
            
            // 에러 메시지 저장
            String errorCode = json.path("code").asText("UNKNOWN");
            String errorMessage = json.path("message").asText("An error occurred");
            
            context.assistant.updateContent(context.answerBuf.toString());
            context.assistant.setIsError(true);
            // 에러 정보는 로그에만 기록
            log.error("AI server error: code={}, message={}", errorCode, errorMessage);
            
            chatMessageRepository.save(context.assistant);
        } catch (Exception e) {
            log.error("Failed to process error event", e);
        } finally {
            safeComplete(context.emitter);
        }
    }

    /**
     * 메시지를 메트릭 정보와 함께 저장합니다.
     */
    private void saveMessageWithMetrics(JsonNode doneJson, StreamContext context) {
        try {
            context.assistant.updateContent(context.answerBuf.toString());
            
            // AI 서버의 메트릭 정보 저장
            if (doneJson.has("total_tokens")) {
                int totalTokens = doneJson.path("total_tokens").asInt(0);
                // total_tokens는 출력 토큰 수로 간주 (또는 tokensOut에 저장)
                context.assistant.setTokensOut(totalTokens);
            }
            
            if (doneJson.has("elapsed_ms")) {
                long elapsedMs = doneJson.path("elapsed_ms").asLong(0);
                context.assistant.setResponseTimeMs(elapsedMs);
            }
            
            if (context.model != null) {
                context.assistant.setLlmModel(context.model);
            }
            
            context.assistant.setIsError(false);
            chatMessageRepository.save(context.assistant);
            
            log.debug("Message saved with metrics: messageId={}, tokens={}, elapsedMs={}",
                context.assistant.getId(),
                context.assistant.getTokensOut(),
                context.assistant.getResponseTimeMs());
        } catch (Exception e) {
            log.error("Failed to save message with metrics", e);
        }
    }

    /**
     * 스트림 완료 처리 (AI 서버가 done 이벤트를 보내지 않고 완료된 경우)
     * 정상적인 경우에는 handleDoneEvent에서 처리됩니다.
     */
    private void handleComplete(StreamContext context) {
        try {
            // AI 서버의 done 이벤트를 받지 못한 경우에만 실행
            if (!context.isDoneReceived) {
                context.assistant.updateContent(context.answerBuf.toString());
                context.assistant.setIsError(false);
                chatMessageRepository.save(context.assistant);
                
                log.warn("Stream completed without done event from AI server");
            }
        } catch (Exception e) {
            log.error("final save error", e);
        } finally {
            safeComplete(context.emitter);
        }
    }

    /**
     * 스트림 에러 처리 (네트워크 에러 등)
     * AI 서버의 error 이벤트가 아닌 백엔드 레벨 에러입니다.
     */
    private void handleStreamError(Throwable error, StreamContext context) {
        log.error("Stream error occurred", error);
        try {
            // 백엔드 레벨 에러를 SSE error 이벤트로 전달
            String errorMessage = error.getMessage() != null 
                ? error.getMessage() 
                : "An error occurred during streaming";
            
            // AI 서버 형식과 유사하게 에러 이벤트 생성
            Map<String, String> errorEvent = new HashMap<>();
            errorEvent.put("type", "error");
            errorEvent.put("code", "INTERNAL_ERROR");
            errorEvent.put("message", errorMessage);
            
            String errorJson = objectMapper.writeValueAsString(errorEvent);
            safeSend(context.emitter, SseEmitter.event()
                .name("error")
                .data(errorJson));
            
            // 에러 상태로 메시지 저장
            context.assistant.updateContent(context.answerBuf.toString());
            context.assistant.setIsError(true);
            chatMessageRepository.save(context.assistant);
        } catch (Exception e) {
            log.warn("Failed to send error event to SSE emitter", e);
        } finally {
            safeComplete(context.emitter);
        }
    }

    /**
     * SseEmitter에 안전하게 데이터를 전송합니다.
     * 이미 완료된 emitter에 대한 send 시도를 방지합니다.
     *
     * @param emitter SseEmitter 인스턴스
     * @param event 전송할 SSE 이벤트
     */
    private void safeSend(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IllegalStateException e) {
            // 이미 완료된 emitter에 대한 send 시도
            log.debug("SSE emitter already completed, skipping send: {}", e.getMessage());
        } catch (IOException e) {
            log.warn("IO error while sending SSE event", e);
        } catch (Exception e) {
            log.warn("Unexpected error while sending SSE event", e);
        }
    }

    /**
     * SseEmitter를 안전하게 완료합니다.
     * 이미 완료된 emitter에 대한 complete 시도를 방지합니다.
     *
     * @param emitter SseEmitter 인스턴스
     */
    private void safeComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (IllegalStateException e) {
            // 이미 완료된 emitter
            log.debug("SSE emitter already completed: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("Error completing SSE emitter", e);
        }
    }

    /**
     * 스트림 처리 컨텍스트
     * 여러 메서드 간 상태를 공유하기 위한 내부 클래스
     */
    private static class StreamContext {
        final SseEmitter emitter;
        final ChatMessage assistant;
        final StringBuilder answerBuf;
        String model;
        boolean isDoneReceived = false;

        StreamContext(SseEmitter emitter, ChatMessage assistant, StringBuilder answerBuf) {
            this.emitter = emitter;
            this.assistant = assistant;
            this.answerBuf = answerBuf;
        }
    }
}
