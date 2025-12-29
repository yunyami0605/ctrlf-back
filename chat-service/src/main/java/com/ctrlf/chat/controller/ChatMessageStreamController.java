package com.ctrlf.chat.controller;

import com.ctrlf.chat.service.ChatStreamService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 채팅 메시지 스트리밍 관련 REST API 컨트롤러
 *
 * <p>AI 응답을 실시간으로 스트리밍하기 위한
 * SSE(Server-Sent Events) 엔드포인트를 제공합니다.</p>
 *
 * @author CtrlF Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/chat/messages")
@RequiredArgsConstructor
public class ChatMessageStreamController {

    private final ChatStreamService chatStreamService;

    /**
     * 메시지 스트림 조회 (SSE)
     *
     * <p>특정 메시지의 AI 응답을 실시간으로 스트리밍합니다.</p>
     *
     * @param messageId 스트리밍할 메시지 ID
     * @return SseEmitter (text/event-stream)
     */
    @GetMapping(
        value = "/{messageId}/stream",
        produces = "text/event-stream"
    )
    public SseEmitter stream(@PathVariable UUID messageId) {
        // ✅ emitter 생성 및 생명주기는 Service가 담당
        return chatStreamService.stream(messageId);
    }
}
