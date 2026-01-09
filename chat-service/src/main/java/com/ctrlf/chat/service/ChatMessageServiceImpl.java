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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
    private final com.ctrlf.chat.elasticsearch.service.FaqLogElasticsearchService faqLogElasticsearchService;
    private final com.ctrlf.chat.elasticsearch.service.ChatLogElasticsearchService chatLogElasticsearchService;
    private final ChatAiClient chatAiClient;

    @Override
    public ChatMessageSendResponse sendMessage(
        ChatMessageSendRequest request,
        UUID userId,
        String domain,
        String department
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
        // department 설정
        userMessage.setDepartment(department);
        chatMessageRepository.save(userMessage);
        
        // Elasticsearch chat_log 인덱스에 실시간 저장
        chatLogElasticsearchService.saveChatLog(userMessage, session, userId.toString(), domain, department);

        // 2️⃣ AI Gateway 호출 (응답 시간 측정)
        // Backend는 Frontend로부터 전달받은 model 값을 그대로 전달 (해석하지 않음)
        String embeddingModel = request.model();
        if (embeddingModel == null) {
            // 요청에 model이 없으면 세션에 저장된 모델 사용 (하위 호환성)
            embeddingModel = session.getEmbeddingModel();
            if (embeddingModel == null) {
                // 세션에도 없으면 기본값 사용
                embeddingModel = "openai";
                log.warn(
                    "모델이 지정되지 않음. 기본값(openai) 사용: sessionId={}",
                    request.sessionId()
                );
            }
        }

        // 세션에 저장된 LLM 모델 사용 (관리자 대시보드에서 설정)
        String llmModel = session.getLlmModel();

        long startTime = System.currentTimeMillis();
        ChatAiResponse aiResponse;
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
                    embeddingModel,  // Frontend에서 전달받은 model 값 그대로 전달
                    llmModel         // 관리자 대시보드에서 선택한 LLM 모델
                );
        } catch (Exception e) {
            log.error("[AI] call failed: {}", e.getMessage(), e);
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
        
        // Elasticsearch chat_log 인덱스에 실시간 저장
        chatLogElasticsearchService.saveChatLog(assistantMessage, session, userId.toString(), domain, department);

        // 4️⃣ USER 메시지에 PII 감지 정보 업데이트
        // AI Gateway 응답의 meta.masked 정보를 user 메시지의 piiDetected에 반영
        if (aiResponse.getMeta() != null && aiResponse.getMeta().getMasked() != null) {
            userMessage.setPiiDetected(aiResponse.getMeta().getMasked());
            chatMessageRepository.save(userMessage);
        }

        // 4️⃣ 응답 반환 (sources, action 포함)
        var action = (aiResponse.getMeta() != null) ? aiResponse.getMeta().getAction() : null;
        log.info("[AI Response Debug] meta={}, action={}",
            aiResponse.getMeta() != null ? "present" : "null",
            action != null ? action.getType() : "null");
        return new ChatMessageSendResponse(
            assistantMessage.getId(),
            assistantMessage.getRole(),
            assistantMessage.getContent(),
            assistantMessage.getCreatedAt(),
            aiResponse.getSources(),  // RAG 출처 정보
            action                    // 프론트엔드 액션 (영상 재생 등)
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
    public ChatMessage retryMessage(UUID sessionId, UUID messageId, String department) {
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
        // 재시도 시에는 세션에 저장된 모델 사용
        String embeddingModel = session.getEmbeddingModel();
        if (embeddingModel == null) {
            // 세션에 모델이 없으면 기본값 사용
            embeddingModel = "openai";
            log.warn(
                "세션에 모델이 할당되지 않음. 기본값(openai) 사용: sessionId={}",
                sessionId
            );
        }

        // 세션에 저장된 LLM 모델 사용 (관리자 대시보드에서 설정)
        String llmModel = session.getLlmModel();

        long startTime = System.currentTimeMillis();
        ChatAiResponse aiResponse;
        try {
            aiResponse = chatAiClient.ask(
                sessionId,
                session.getUserUuid(),
                "EMPLOYEE",   // TODO: JWT에서 추출
                department,
                session.getDomain(),
                "WEB",
                userMessage.getContent(),
                embeddingModel,  // 세션에 저장된 모델 사용
                llmModel         // 관리자 대시보드에서 선택한 LLM 모델
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

    @Override
    @Transactional(readOnly = true)
    public com.ctrlf.chat.dto.response.AdminMessageLogResponse getAdminMessages(
        String domain,
        Integer daysBack
    ) {
        // Elasticsearch에서 FAQ 로그 조회 (faq_log 인덱스)
        return faqLogElasticsearchService.getFaqLogs(domain, daysBack);
    }

    /**
     * 기존 PostgreSQL 조회 방식 (레거시, 참고용)
     * 
     * @deprecated Elasticsearch faq_log 인덱스에서 조회하도록 변경됨
     */
    @Deprecated
    private com.ctrlf.chat.dto.response.AdminMessageLogResponse getAdminMessagesFromPostgres(
        String domain,
        Integer daysBack
    ) {
        // 기본값 설정
        int actualDaysBack = (daysBack != null && daysBack > 0) ? daysBack : 30;
        Instant startDate = Instant.now().minusSeconds(actualDaysBack * 24L * 60L * 60L);

        log.info("[관리자 질문 로그 조회] domain={}, daysBack={}, startDate={}", domain, actualDaysBack, startDate);

        // 질문 로그 조회
        List<Object[]> results = chatMessageRepository.findUserMessagesForFaqGeneration(startDate, domain);

        // DTO 변환
        List<com.ctrlf.chat.dto.response.AdminMessageLogResponse.MessageLogItem> items = new ArrayList<>();
        for (Object[] row : results) {
            // null 안전 처리
            String domainValue = row[5] != null ? (String) row[5] : "ETC";
            String contentValue = row[2] != null ? (String) row[2] : "";
            
            items.add(new com.ctrlf.chat.dto.response.AdminMessageLogResponse.MessageLogItem(
                (UUID) row[0],           // id
                (UUID) row[1],           // session_id
                contentValue,             // content (null 방지)
                (String) row[3],         // keyword
                domainValue,              // domain (null 방지, "ETC"로 기본값 설정)
                (UUID) row[6],           // user_uuid (s.user_uuid)
                ((java.sql.Timestamp) row[4]).toInstant()  // created_at
            ));
        }

        log.info("[관리자 질문 로그 조회] 조회 완료: totalCount={}", items.size());

        // 디버깅: 사용자별 질문 수 통계
        Map<UUID, Long> userQuestionCount = items.stream()
            .filter(item -> item.getUserId() != null)  // null userId 필터링
            .collect(Collectors.groupingBy(
                item -> item.getUserId(),  // null 체크 후 사용
                Collectors.counting()
            ));
        log.info("[관리자 질문 로그 조회] 사용자별 질문 수: totalUsers={}, userCounts={}", 
            userQuestionCount.size(), 
            userQuestionCount.entrySet().stream()
                .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
                .limit(10)
                .collect(Collectors.toMap(
                    e -> e.getKey().toString().substring(0, 8) + "...",
                    Map.Entry::getValue
                )));

        // 디버깅: 질문별 빈도 통계 (상위 10개)
        Map<String, Long> contentFrequency = items.stream()
            .filter(item -> item.getContent() != null)  // null content 필터링
            .collect(Collectors.groupingBy(
                item -> item.getContent() != null ? item.getContent() : "",  // null 안전 처리
                Collectors.counting()
            ));
        log.info("[관리자 질문 로그 조회] 질문별 빈도 (상위 10개): {}", 
            contentFrequency.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(e -> String.format("'%s': %d회", 
                    e.getKey().length() > 50 ? e.getKey().substring(0, 50) + "..." : e.getKey(),
                    e.getValue()))
                .collect(Collectors.joining(", ")));

        // 디버깅: 여러 사용자가 질문한 질문 확인 (minFrequency=3 기준)
        Map<String, Set<UUID>> contentUserMap = new HashMap<>();
        for (com.ctrlf.chat.dto.response.AdminMessageLogResponse.MessageLogItem item : items) {
            // null 안전 처리: content가 null이면 빈 문자열 사용
            String contentKey = item.getContent() != null ? item.getContent() : "";
            contentUserMap.computeIfAbsent(contentKey, k -> new HashSet<>())
                .add(item.getUserId());
        }
        long multiUserQuestions = contentUserMap.values().stream()
            .filter(users -> users.size() >= 3)
            .count();
        log.info("[관리자 질문 로그 조회] 여러 사용자(3명 이상)가 질문한 항목 수: {}", multiUserQuestions);

        // 샘플 메시지 로그 (최대 5개)
        if (!items.isEmpty()) {
            log.info("[관리자 질문 로그 조회] 샘플 메시지 (최대 5개): {}", 
                items.stream()
                    .limit(5)
                    .map(m -> {
                        String userIdStr = m.getUserId() != null ? m.getUserId().toString().substring(0, 8) + "..." : "null";
                        String contentStr = m.getContent() != null 
                            ? (m.getContent().length() > 50 ? m.getContent().substring(0, 50) + "..." : m.getContent())
                            : "(empty)";
                        return String.format("userId=%s, content='%s'", userIdStr, contentStr);
                    })
                    .collect(Collectors.joining(" | ")));
        }

        return new com.ctrlf.chat.dto.response.AdminMessageLogResponse(items, items.size());
    }
}
