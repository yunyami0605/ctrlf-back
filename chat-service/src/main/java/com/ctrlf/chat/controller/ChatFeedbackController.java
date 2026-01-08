package com.ctrlf.chat.controller;

import com.ctrlf.chat.dto.request.ChatFeedbackRequest;
import com.ctrlf.chat.service.ChatFeedbackService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * 채팅 메시지 피드백 관련 REST API 컨트롤러
 * 
 * <p>사용자가 특정 메시지에 대한 피드백(평점, 코멘트)을 제출하는 기능을 제공합니다.</p>
 * 
 * @author CtrlF Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatFeedbackController {

    private final ChatFeedbackService chatFeedbackService;

    /**
     * 메시지 피드백 제출
     * 
     * <p>특정 메시지에 대한 사용자 피드백(평점, 코멘트)을 저장합니다.</p>
     * 
     * @param sessionId 세션 ID
     * @param messageId 피드백을 제출할 메시지 ID
     * @param request 피드백 요청 (평점, 코멘트)
     * @param jwt JWT 토큰 (사용자 정보 포함)
     * @return 200 OK
     */
    @PostMapping("/sessions/{sessionId}/messages/{messageId}/feedback")
    public ResponseEntity<Void> messageFeedback(
        @PathVariable UUID sessionId,
        @PathVariable UUID messageId,
        @Valid @RequestBody ChatFeedbackRequest request,
        @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userUuid = UUID.fromString(jwt.getSubject());

        chatFeedbackService.submitMessageFeedback(
            sessionId,
            messageId,
            userUuid,
            request.score(),
            request.comment()
        );

        return ResponseEntity.ok().build();
    }
}
