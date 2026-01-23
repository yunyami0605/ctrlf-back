package com.ctrlf.chat.service;

// import com.ctrlf.chat.ai.search.client.ChatAiClient; // ⚠️ session-summary 기능 주석 처리로 인해 사용 안 함
import com.ctrlf.chat.dto.request.ChatSessionCreateRequest;
import com.ctrlf.chat.dto.request.ChatSessionUpdateRequest;
import com.ctrlf.chat.dto.response.ChatSessionHistoryResponse;
import com.ctrlf.chat.dto.response.ChatSessionResponse;
// import com.ctrlf.chat.dto.summary.ChatSessionSummaryResponse; // ⚠️ session-summary 기능 주석 처리로 인해 사용 안 함
import com.ctrlf.chat.config.metrics.CustomMetrics;
import com.ctrlf.chat.entity.ChatMessage;
import com.ctrlf.chat.entity.ChatSession;
import com.ctrlf.chat.exception.chat.ChatSessionNotFoundException;
import com.ctrlf.chat.repository.ChatMessageRepository;
import com.ctrlf.chat.repository.ChatSessionRepository;
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
public class ChatSessionServiceImpl implements ChatSessionService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final CustomMetrics customMetrics;

    // ⚠️ AI 요약용 Client (현재 사용 안 함 - session-summary 기능 주석 처리)
    // private final ChatAiClient chatAiClient;

    @Override
    public ChatSessionResponse createSession(ChatSessionCreateRequest request) {
        ChatSession session = new ChatSession();
        session.setTitle(request.title());
        session.setDomain(request.domain());
        session.setUserUuid(request.userUuid());
        // 모델은 Frontend에서 POST /api/chat/sessions/{sessionId}/model로 설정

        ChatSession saved = chatSessionRepository.save(session);

        // 메트릭 기록
        customMetrics.incrementChatSessionsCreated();

        return new ChatSessionResponse(
            saved.getId(),
            saved.getTitle(),
            saved.getDomain(),
            saved.getUserUuid(),
            saved.getCreatedAt(),
            saved.getUpdatedAt()
        );
    }

    @Override
    public ChatSessionResponse getSession(UUID sessionId) {
        ChatSession session = chatSessionRepository.findActiveById(sessionId);
        if (session == null) {
            throw new ChatSessionNotFoundException();
        }

        return new ChatSessionResponse(
            session.getId(),
            session.getTitle(),
            session.getDomain(),
            session.getUserUuid(),
            session.getCreatedAt(),
            session.getUpdatedAt()
        );
    }

    @Override
    public List<ChatSessionResponse> getSessionList() {
        return chatSessionRepository.findAllActive()
            .stream()
            .map(session -> new ChatSessionResponse(
                session.getId(),
                session.getTitle(),
                session.getDomain(),
                session.getUserUuid(),
                session.getCreatedAt(),
                session.getUpdatedAt()
            ))
            .toList();
    }

    @Override
    public ChatSessionResponse updateSession(UUID sessionId, ChatSessionUpdateRequest request) {
        ChatSession session = chatSessionRepository.findActiveById(sessionId);
        if (session == null) {
            throw new ChatSessionNotFoundException();
        }

        session.updateTitle(request.title());

        return new ChatSessionResponse(
            session.getId(),
            session.getTitle(),
            session.getDomain(),
            session.getUserUuid(),
            session.getCreatedAt(),
            session.getUpdatedAt()
        );
    }

    /**
     * ✅ 세션 종료 시점
     * - 전체 채팅 메시지를 AI 서버로 전송
     * - 요약 결과를 세션에 저장
     * - 이후 soft delete 처리
     */
    @Override
    public void deleteSession(UUID sessionId) {
        ChatSession session = chatSessionRepository.findActiveById(sessionId);
        if (session == null) {
            throw new ChatSessionNotFoundException();
        }

        // 1. 세션 메시지 전체 조회
        List<ChatMessage> messages =
            chatMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(sessionId);

        // 2. AI 요약 호출 (현재 AI 서비스에 해당 엔드포인트가 없어 주석 처리)
        // AI 서비스에는 FAQ 관련 API만 제공되며, /ai/chat/session-summary 엔드포인트는 구현되지 않음
        /*
        try {
            ChatSessionSummaryResponse summary =
                chatAiClient.summarizeSession(sessionId, messages);

            session.setSummary(summary.getSummary());
            session.setIntent(summary.getIntent());

        } catch (Exception e) {
            log.warn(
                "AI session summary failed. sessionId={}",
                sessionId,
                e
            );
        }
        */

        // 3. 세션 종료 처리
        session.softDelete();
    }

    // ✅ 세션 히스토리(전체) 조회 유지
    @Override
    public ChatSessionHistoryResponse getSessionHistory(UUID sessionId) {
        ChatSession session = chatSessionRepository.findActiveById(sessionId);
        if (session == null) {
            throw new ChatSessionNotFoundException();
        }

        List<ChatMessage> messages =
            chatMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(sessionId);

        return new ChatSessionHistoryResponse(
            session.getId(),
            session.getTitle(),
            messages
        );
    }

    @Override
    public void setSessionModel(UUID sessionId, String model) {
        ChatSession session = chatSessionRepository.findActiveById(sessionId);
        if (session == null) {
            throw new ChatSessionNotFoundException();
        }

        // Backend는 모델 값을 검증하고 그대로 저장 (해석하지 않음)
        session.setEmbeddingModel(model);
        log.debug(
            "세션 모델 설정: sessionId={}, model={}",
            sessionId,
            model
        );
    }

    @Override
    public void setSessionLlmModel(UUID sessionId, String llmModel) {
        ChatSession session = chatSessionRepository.findActiveById(sessionId);
        if (session == null) {
            throw new ChatSessionNotFoundException();
        }

        // 유효한 LLM 모델 값 검증
        if (llmModel != null && !llmModel.equals("exaone") && !llmModel.equals("openai")) {
            throw new IllegalArgumentException(
                "유효하지 않은 LLM 모델입니다: " + llmModel + " (허용값: exaone, openai)"
            );
        }

        session.setLlmModel(llmModel);
        log.debug(
            "세션 LLM 모델 설정: sessionId={}, llmModel={}",
            sessionId,
            llmModel
        );
    }
}
