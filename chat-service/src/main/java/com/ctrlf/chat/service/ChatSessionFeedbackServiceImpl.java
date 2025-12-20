package com.ctrlf.chat.service;

import com.ctrlf.chat.entity.ChatSessionFeedback;
import com.ctrlf.chat.entity.ChatSession;
import com.ctrlf.chat.repository.ChatSessionFeedbackRepository;
import com.ctrlf.chat.repository.ChatSessionRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ChatSessionFeedbackServiceImpl implements ChatSessionFeedbackService {

    private final ChatSessionFeedbackRepository chatSessionFeedbackRepository;
    private final ChatSessionRepository chatSessionRepository;

    @Override
    public void submitSessionFeedback(
        UUID sessionId,
        UUID userUuid,
        Integer score,
        String comment
    ) {
        // 세션 존재 여부 검증
        ChatSession session = chatSessionRepository.findActiveById(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("세션을 찾을 수 없습니다: " + sessionId);
        }

        ChatSessionFeedback feedback =
            chatSessionFeedbackRepository.findBySessionId(sessionId)
                .orElseGet(ChatSessionFeedback::new);

        feedback.setSessionId(sessionId);
        feedback.setUserUuid(userUuid);
        feedback.setScore(score);
        feedback.setComment(comment);
        feedback.setCreatedAt(Instant.now());

        chatSessionFeedbackRepository.save(feedback);
    }
}
