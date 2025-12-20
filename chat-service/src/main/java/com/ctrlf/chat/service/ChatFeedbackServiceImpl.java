package com.ctrlf.chat.service;

import com.ctrlf.chat.entity.ChatFeedback;
import com.ctrlf.chat.entity.ChatMessage;
import com.ctrlf.chat.entity.ChatSession;
import com.ctrlf.chat.repository.ChatFeedbackRepository;
import com.ctrlf.chat.repository.ChatMessageRepository;
import com.ctrlf.chat.repository.ChatSessionRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ChatFeedbackServiceImpl implements ChatFeedbackService {

    private final ChatFeedbackRepository chatFeedbackRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;

    @Override
    public void submitMessageFeedback(
        UUID sessionId,
        UUID messageId,
        UUID userUuid,
        Integer score,
        String comment
    ) {
        // 세션 존재 여부 검증
        ChatSession session = chatSessionRepository.findActiveById(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("세션을 찾을 수 없습니다: " + sessionId);
        }

        // 메시지 존재 여부 및 세션 일치 검증
        ChatMessage message = chatMessageRepository.findById(messageId)
            .orElseThrow(() -> new IllegalArgumentException("메시지를 찾을 수 없습니다: " + messageId));
        
        if (!sessionId.equals(message.getSessionId())) {
            throw new IllegalArgumentException("메시지가 해당 세션에 속하지 않습니다.");
        }

        ChatFeedback feedback = new ChatFeedback();
        feedback.setSessionId(sessionId);
        feedback.setMessageId(messageId);
        feedback.setUserUuid(userUuid);
        feedback.setScore(score);
        feedback.setComment(comment);
        feedback.setCreatedAt(Instant.now());

        chatFeedbackRepository.save(feedback);
    }
}
