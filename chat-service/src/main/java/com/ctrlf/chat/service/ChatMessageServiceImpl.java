package com.ctrlf.chat.service;

import com.ctrlf.chat.ai.search.client.ChatAiClient;
import com.ctrlf.chat.ai.search.dto.ChatAiResponse;
import com.ctrlf.chat.dto.request.ChatMessageSendRequest;
import com.ctrlf.chat.dto.response.ChatMessageCursorResponse;
import com.ctrlf.chat.dto.response.ChatMessageSendResponse;
import com.ctrlf.chat.entity.ChatMessage;
import com.ctrlf.chat.entity.ChatSession;
import com.ctrlf.chat.repository.ChatMessageRepository;
import com.ctrlf.chat.repository.ChatSessionRepository;
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
    private final ChatSessionRepository chatSessionRepository;
    private final ChatAiClient chatAiClient;

    @Override
    public ChatMessageSendResponse sendMessage(
        ChatMessageSendRequest request,
        UUID userId,
        String domain
    ) {
        // 0️⃣ 세션 존재 여부 검증
        ChatSession session = chatSessionRepository.findActiveById(request.sessionId());
        if (session == null) {
            throw new IllegalArgumentException("세션을 찾을 수 없습니다: " + request.sessionId());
        }

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

        List<ChatMessage> rows;
        
        if (cursor != null && !cursor.isBlank()) {
            ParsedCursor parsed = ParsedCursor.parse(cursor);
            Instant cursorCreatedAt = parsed.createdAt();
            UUID cursorId = parsed.id();
            
            rows = chatMessageRepository.findNextPageBySessionId(
                sessionId,
                cursorCreatedAt,
                cursorId,
                limit
            );
        } else {
            // 첫 페이지 조회
            rows = chatMessageRepository.findFirstPageBySessionId(
                sessionId,
                limit
            );
        }

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
        // 1️⃣ 재시도할 메시지 조회 (assistant 메시지여야 함)
        ChatMessage targetMessage = chatMessageRepository.findById(messageId)
            .orElseThrow(() -> new IllegalArgumentException("메시지를 찾을 수 없습니다: " + messageId));
        
        if (!"assistant".equals(targetMessage.getRole())) {
            throw new IllegalArgumentException("재시도는 assistant 메시지만 가능합니다.");
        }
        
        if (!sessionId.equals(targetMessage.getSessionId())) {
            throw new IllegalArgumentException("세션 ID가 일치하지 않습니다.");
        }
        
        // 2️⃣ 세션 정보 조회 (도메인, 사용자 정보)
        ChatSession session = chatSessionRepository.findActiveById(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("세션을 찾을 수 없습니다: " + sessionId);
        }
        
        // 3️⃣ 재시도할 메시지의 이전 user 메시지 찾기
        List<ChatMessage> allMessages = 
            chatMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(sessionId);
        
        ChatMessage userMessage = null;
        for (int i = 0; i < allMessages.size(); i++) {
            if (allMessages.get(i).getId().equals(messageId)) {
                // targetMessage 이전의 user 메시지 찾기
                for (int j = i - 1; j >= 0; j--) {
                    if ("user".equals(allMessages.get(j).getRole())) {
                        userMessage = allMessages.get(j);
                        break;
                    }
                }
                break;
            }
        }
        
        if (userMessage == null) {
            throw new IllegalArgumentException("재시도할 user 메시지를 찾을 수 없습니다.");
        }
        
        // 4️⃣ AI Gateway에 재요청
        ChatAiResponse aiResponse;
        try {
            aiResponse = chatAiClient.ask(
                sessionId,
                session.getUserUuid(),
                "EMPLOYEE",   // TODO: JWT에서 추출
                null,         // department
                session.getDomain(),
                "WEB",
                userMessage.getContent()
            );
        } catch (Exception e) {
            log.error("[AI] retry failed", e);
            throw new RuntimeException("AI 재시도 요청 실패: " + e.getMessage(), e);
        }
        
        // 5️⃣ 기존 메시지 업데이트
        targetMessage.updateContent(aiResponse.getAnswer());
        targetMessage.setTokensIn(aiResponse.getPromptTokens());
        targetMessage.setTokensOut(aiResponse.getCompletionTokens());
        targetMessage.setLlmModel(aiResponse.getModel());
        
        return chatMessageRepository.save(targetMessage);
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
