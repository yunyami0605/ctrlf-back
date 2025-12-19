package com.ctrlf.chat.controller;

import com.ctrlf.chat.service.ChatStreamService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 채팅 메시지 스트리밍 관련 REST API 컨트롤러
 * 
 * <p>AI 응답을 실시간으로 스트리밍하기 위한 SSE(Server-Sent Events) 엔드포인트를 제공합니다.</p>
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
     * <p>SSE(Server-Sent Events) 프로토콜을 사용하여 클라이언트에 실시간으로 데이터를 전송합니다.</p>
     * 
     * @param messageId 스트리밍할 메시지 ID
     * @return SSE Emitter (스트리밍 연결)
     */
    @GetMapping("/{messageId}/stream")
    public SseEmitter stream(@PathVariable UUID messageId) {
        SseEmitter emitter = new SseEmitter(0L);
        chatStreamService.stream(messageId, emitter);
        return emitter;
    }
}
