package com.ctrlf.chat.service;

import com.ctrlf.chat.ai.search.client.ChatAiClient;
import com.ctrlf.chat.dto.request.ChatSessionCreateRequest;
import com.ctrlf.chat.dto.request.ChatSessionUpdateRequest;
import com.ctrlf.chat.dto.response.ChatSessionHistoryResponse;
import com.ctrlf.chat.dto.response.ChatSessionResponse;
import com.ctrlf.chat.dto.summary.ChatSessionSummaryResponse;
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

    // ✅ AI 요약용 Client 추가
    private final ChatAiClient chatAiClient;

    @Override
    public ChatSessionResponse createSession(ChatSessionCreateRequest request) {
        ChatSession session = new ChatSession();
        session.setTitle(request.title());
        session.setDomain(request.domain());
        session.setUserUuid(request.userUuid());

        ChatSession saved = chatSessionRepository.save(session);

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

        // 2. AI 요약 호출 (실패해도 세션 종료는 진행)
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
}
