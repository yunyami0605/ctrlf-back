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
        // 키워드 추출 및 설정
        String keyword = extractKeyword(request.content());
        userMessage.setKeyword(keyword);
        // PII 감지는 AI 응답 후에 설정 (아래에서 처리)
        // TODO: JWT에서 department 추출하여 설정
        // userMessage.setDepartment(department);
        chatMessageRepository.save(userMessage);

        // 2️⃣ AI Gateway 호출 (응답 시간 측정)
        long startTime = System.currentTimeMillis();
        ChatAiResponse aiResponse;
        String department = null; // TODO: JWT에서 추출
        try {
            aiResponse =
                chatAiClient.ask(
                    request.sessionId(),
                    userId,
                    "EMPLOYEE",   // TODO: JWT에서 추출
                    department,
                    domain,
                    "WEB",
                    request.content(),
                    request.model()  // A/B 테스트용 model 전달
                );
        } catch (Exception e) {
            log.error("[AI] call failed", e);
            long responseTime = System.currentTimeMillis() - startTime;

            ChatMessage fallbackMessage =
                ChatMessage.assistantMessage(
                    request.sessionId(),
                    "현재 AI 응답을 제공할 수 없습니다.",
                    null,
                    null,
                    null
                );
            // 에러 메시지 정보 설정
            fallbackMessage.setRoutingType("OTHER");
            fallbackMessage.setDepartment(department);
            fallbackMessage.setResponseTimeMs(responseTime);
            fallbackMessage.setIsError(true);
            chatMessageRepository.save(fallbackMessage);

            return new ChatMessageSendResponse(
                fallbackMessage.getId(),
                fallbackMessage.getRole(),
                fallbackMessage.getContent(),
                fallbackMessage.getCreatedAt()
            );
        }
        long responseTime = System.currentTimeMillis() - startTime;

        // 3️⃣ ASSISTANT 메시지 저장
        ChatMessage assistantMessage =
            ChatMessage.assistantMessage(
                request.sessionId(),
                aiResponse.getAnswer(),
                aiResponse.getPromptTokens(),
                aiResponse.getCompletionTokens(),
                aiResponse.getModel()
            );
        // 대시보드 필드 설정
        // 라우팅 타입 설정 (AI Gateway 응답에서 가져오거나 기본값 "OTHER")
        String routingType = "OTHER";
        if (aiResponse.getMeta() != null && aiResponse.getMeta().getRoute() != null) {
            routingType = aiResponse.getMeta().getRoute().toUpperCase();
        }
        assistantMessage.setRoutingType(routingType);
        assistantMessage.setDepartment(department);
        assistantMessage.setResponseTimeMs(responseTime);
        assistantMessage.setIsError(false);
        chatMessageRepository.save(assistantMessage);

        // 4️⃣ USER 메시지에 PII 감지 정보 업데이트
        // AI Gateway 응답의 meta.masked 정보를 user 메시지의 piiDetected에 반영
        if (aiResponse.getMeta() != null && aiResponse.getMeta().getMasked() != null) {
            userMessage.setPiiDetected(aiResponse.getMeta().getMasked());
            chatMessageRepository.save(userMessage);
        }

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
        
        // 4️⃣ AI Gateway에 재요청 (응답 시간 측정)
        long startTime = System.currentTimeMillis();
        ChatAiResponse aiResponse;
        String department = null; // TODO: JWT에서 추출
        try {
            aiResponse = chatAiClient.ask(
                sessionId,
                session.getUserUuid(),
                "EMPLOYEE",   // TODO: JWT에서 추출
                department,
                session.getDomain(),
                "WEB",
                userMessage.getContent()
            );
        } catch (Exception e) {
            log.error("[AI] retry failed", e);
            long responseTime = System.currentTimeMillis() - startTime;
            // 에러 상태로 업데이트
            targetMessage.setIsError(true);
            targetMessage.setResponseTimeMs(responseTime);
            chatMessageRepository.save(targetMessage);
            throw new RuntimeException("AI 재시도 요청 실패: " + e.getMessage(), e);
        }
        long responseTime = System.currentTimeMillis() - startTime;
        
        // 5️⃣ 기존 메시지 업데이트
        targetMessage.updateContent(aiResponse.getAnswer());
        targetMessage.setTokensIn(aiResponse.getPromptTokens());
        targetMessage.setTokensOut(aiResponse.getCompletionTokens());
        targetMessage.setLlmModel(aiResponse.getModel());
        // 대시보드 필드 업데이트
        // 라우팅 타입 설정 (AI Gateway 응답에서 가져오거나 기본값 "OTHER")
        String routingType = "OTHER";
        if (aiResponse.getMeta() != null && aiResponse.getMeta().getRoute() != null) {
            routingType = aiResponse.getMeta().getRoute().toUpperCase();
        }
        if (targetMessage.getRoutingType() == null) {
            targetMessage.setRoutingType(routingType);
        }
        targetMessage.setDepartment(department);
        targetMessage.setResponseTimeMs(responseTime);
        targetMessage.setIsError(false);
        
        ChatMessage savedMessage = chatMessageRepository.save(targetMessage);

        // 6️⃣ USER 메시지에 PII 감지 정보 업데이트
        // AI Gateway 응답의 meta.masked 정보를 user 메시지의 piiDetected에 반영
        if (aiResponse.getMeta() != null && aiResponse.getMeta().getMasked() != null) {
            userMessage.setPiiDetected(aiResponse.getMeta().getMasked());
            chatMessageRepository.save(userMessage);
        }
        
        return savedMessage;
    }

    /**
     * 메시지 내용에서 키워드 추출
     * 
     * <p>간단한 키워드 추출 로직: 불필요한 조사, 어미 제거 후 주요 단어 추출</p>
     * 
     * @param content 메시지 내용
     * @return 추출된 키워드 (최대 200자)
     */
    private String extractKeyword(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        
        // 공백 제거 및 정리
        String cleaned = content.trim();
        
        // 너무 짧은 경우 그대로 반환
        if (cleaned.length() <= 10) {
            return cleaned.length() > 200 ? cleaned.substring(0, 200) : cleaned;
        }
        
        // 불필요한 조사, 어미 제거 (간단한 패턴)
        // "은", "는", "이", "가", "을", "를", "의", "에", "에서", "로", "으로" 등 제거
        String[] stopWords = {
            "은 ", "는 ", "이 ", "가 ", "을 ", "를 ", "의 ", "에 ", "에서 ", "로 ", "으로 ",
            "에게 ", "께 ", "한테 ", "에게서 ", "한테서 ", "와 ", "과 ", "하고 ", "도 ", "만 ",
            "부터 ", "까지 ", "에서부터 ", "조차 ", "마저 ", "뿐 ", "따라 ", "마다 "
        };
        
        String keyword = cleaned;
        for (String stopWord : stopWords) {
            keyword = keyword.replace(stopWord, " ");
        }
        
        // 연속된 공백 제거
        keyword = keyword.replaceAll("\\s+", " ").trim();
        
        // 최대 200자로 제한
        if (keyword.length() > 200) {
            keyword = keyword.substring(0, 200).trim();
            // 마지막 단어가 잘릴 수 있으므로 마지막 공백 기준으로 자르기
            int lastSpace = keyword.lastIndexOf(' ');
            if (lastSpace > 0) {
                keyword = keyword.substring(0, lastSpace);
            }
        }
        
        return keyword.isBlank() ? cleaned.substring(0, Math.min(200, cleaned.length())) : keyword;
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
