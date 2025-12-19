package com.ctrlf.chat.service;

import com.ctrlf.chat.dto.request.ChatMessageSendRequest;
import com.ctrlf.chat.dto.response.ChatMessageCursorResponse;
import com.ctrlf.chat.dto.response.ChatMessageSendResponse;
import com.ctrlf.chat.entity.ChatMessage;
import java.util.UUID;

/**
 * 채팅 메시지 관련 비즈니스 로직 인터페이스
 * 
 * <p>메시지 전송, 조회, 재시도 등의 기능을 정의합니다.</p>
 * 
 * @author CtrlF Team
 * @since 1.0.0
 */
public interface ChatMessageService {

    /**
     * 메시지 전송 및 AI 응답 생성
     * 
     * @param request 메시지 전송 요청
     * @param userId 사용자 ID
     * @param domain 업무 도메인
     * @return AI 응답 메시지 정보
     */
    ChatMessageSendResponse sendMessage(
        ChatMessageSendRequest request,
        UUID userId,
        String domain
    );

    /**
     * 세션별 메시지 목록 조회 (커서 기반 페이지네이션)
     * 
     * @param sessionId 세션 ID
     * @param cursor 커서 (null이면 첫 페이지)
     * @param size 페이지 크기
     * @return 메시지 목록 및 페이지네이션 정보
     */
    ChatMessageCursorResponse getMessagesBySession(
        UUID sessionId,
        String cursor,
        int size
    );

    /**
     * 메시지 재시도
     * 
     * @param sessionId 세션 ID
     * @param messageId 재시도할 메시지 ID
     * @return 재시도 결과 메시지
     */
    ChatMessage retryMessage(UUID sessionId, UUID messageId);
}
