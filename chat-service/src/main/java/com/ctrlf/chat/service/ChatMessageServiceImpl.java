package com.ctrlf.chat.service;

import com.ctrlf.chat.ai.search.client.ChatAiClient;
import com.ctrlf.chat.ai.search.dto.ChatAiResponse;
import com.ctrlf.chat.dto.request.ChatMessageSendRequest;
import com.ctrlf.chat.dto.response.ChatMessageCursorResponse;
import com.ctrlf.chat.dto.response.ChatMessageSendResponse;
import com.ctrlf.chat.entity.ChatMessage;
import com.ctrlf.chat.repository.ChatMessageRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ChatMessageServiceImpl implements ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatAiClient chatAiClient;

    @Override
    public ChatMessageSendResponse sendMessage(
        ChatMessageSendRequest request,
        UUID userId,
        String domain
    ) {
        // 1️⃣ USER 메시지 저장
        ChatMessage userMessage =
            ChatMessage.userMessage(
                request.sessionId(),
                request.content()
            );
        chatMessageRepository.save(userMessage);

        // 2️⃣ AI Gateway 호출
        ChatAiResponse aiResponse;
        try {
            aiResponse =
                chatAiClient.ask(
                    request.sessionId(),
                    userId,
                    "EMPLOYEE",   // TODO: JWT에서 추출
                    null,         // department
                    domain,
                    "WEB",
                    request.content()
                );
        } catch (Exception e) {
            log.error("[AI] call failed", e);

            ChatMessage fallbackMessage =
                ChatMessage.assistantMessage(
                    request.sessionId(),
                    "현재 AI 응답을 제공할 수 없습니다.",
                    null,
                    null,
                    null
                );
            chatMessageRepository.save(fallbackMessage);

            return new ChatMessageSendResponse(
                fallbackMessage.getId(),
                fallbackMessage.getRole(),
                fallbackMessage.getContent(),
                fallbackMessage.getCreatedAt()
            );
        }

        // 3️⃣ ASSISTANT 메시지 저장
        ChatMessage assistantMessage =
            ChatMessage.assistantMessage(
                request.sessionId(),
                aiResponse.getAnswer(),
                aiResponse.getPromptTokens(),
                aiResponse.getCompletionTokens(),
                aiResponse.getModel()
            );
        chatMessageRepository.save(assistantMessage);

        // 4️⃣ 응답 반환
        return new ChatMessageSendResponse(
            assistantMessage.getId(),
            assistantMessage.getRole(),
            assistantMessage.getContent(),
            assistantMessage.getCreatedAt()
        );
    }

    // ===============================
    // Cursor Pagination (변경 없음)
    // ===============================

    @Override
    @Transactional(readOnly = true)
    public ChatMessageCursorResponse getMessagesBySession(
        UUID sessionId,
        String cursor,
        int size
    ) {
        int safeSize = Math.max(1, Math.min(size, 100));
        int limit = safeSize + 1;

        Instant cursorCreatedAt = null;
        UUID cursorId = null;

        if (cursor != null && !cursor.isBlank()) {
            ParsedCursor parsed = ParsedCursor.parse(cursor);
            cursorCreatedAt = parsed.createdAt();
            cursorId = parsed.id();
        }

        List<ChatMessage> rows =
            chatMessageRepository.findNextPageBySessionId(
                sessionId,
                cursorCreatedAt,
                cursorId,
                limit
            );

        boolean hasNext = rows.size() > safeSize;
        List<ChatMessage> pageDesc =
            hasNext ? rows.subList(0, safeSize) : rows;

        String nextCursor = null;
        if (hasNext && !pageDesc.isEmpty()) {
            ChatMessage oldest =
                pageDesc.get(pageDesc.size() - 1);
            nextCursor =
                ParsedCursor.encode(
                    oldest.getCreatedAt(),
                    oldest.getId()
                );
        }

        List<ChatMessage> pageAsc =
            new ArrayList<>(pageDesc);
        Collections.reverse(pageAsc);

        return new ChatMessageCursorResponse(
            pageAsc,
            nextCursor,
            hasNext
        );
    }

    @Override
    public ChatMessage retryMessage(UUID sessionId, UUID messageId) {
        throw new UnsupportedOperationException("retryMessage not implemented");
    }

    @Override
    public ChatMessage regenMessage(UUID sessionId, UUID messageId) {
        throw new UnsupportedOperationException("regenMessage not implemented");
    }

    /* ===============================
       Cursor Helper
       =============================== */
    private record ParsedCursor(
        Instant createdAt,
        UUID id
    ) {
        static ParsedCursor parse(String cursor) {
            String[] parts = cursor.split("_", 2);
            long millis = Long.parseLong(parts[0]);
            UUID id = UUID.fromString(parts[1]);
            return new ParsedCursor(
                Instant.ofEpochMilli(millis),
                id
            );
        }

        static String encode(
            Instant createdAt,
            UUID id
        ) {
            return createdAt.toEpochMilli() + "_" + id;
        }
    }
}
