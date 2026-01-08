package com.ctrlf.chat.service;

import com.ctrlf.chat.entity.ChatMessage;
import com.ctrlf.chat.entity.ChatSession;
import com.ctrlf.chat.repository.ChatFeedbackRepository;
import com.ctrlf.chat.repository.ChatMessageRepository;
import com.ctrlf.chat.repository.ChatSessionRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
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
        // 1. score 검증 (1 또는 5만 허용)
        if (score == null || (score != 1 && score != 5)) {
            throw new IllegalArgumentException("score는 1 또는 5만 허용됩니다");
        }

        // 2. 세션 존재 여부 검증
        ChatSession session = chatSessionRepository.findActiveById(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("세션을 찾을 수 없습니다: " + sessionId);
        }

        // 3. 세션 소유권 검증 (권한 체크)
        if (!userUuid.equals(session.getUserUuid())) {
            throw new IllegalArgumentException("해당 세션에 대한 권한이 없습니다");
        }

        // 4. 메시지 존재 여부 및 세션 일치 검증
        ChatMessage message = chatMessageRepository.findById(messageId)
            .orElseThrow(() -> new IllegalArgumentException("메시지를 찾을 수 없습니다: " + messageId));

        if (!sessionId.equals(message.getSessionId())) {
            throw new IllegalArgumentException("메시지가 해당 세션에 속하지 않습니다");
        }

        // 5. 원자적 UPSERT (PostgreSQL ON CONFLICT)
        //    - 동시성 문제 없이 INSERT 또는 UPDATE 수행
        //    - race condition 발생해도 DB 레벨에서 안전하게 처리
        int affectedRows = chatFeedbackRepository.upsertFeedback(
            sessionId,
            messageId,
            userUuid,
            score,
            comment
        );

        log.debug("피드백 저장 완료: messageId={}, userUuid={}, score={}, affectedRows={}",
            messageId, userUuid, score, affectedRows);
    }
}
