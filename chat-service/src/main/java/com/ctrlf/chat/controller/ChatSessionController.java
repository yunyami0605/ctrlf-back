package com.ctrlf.chat.controller;

import com.ctrlf.chat.dto.request.ChatSessionCreateRequest;
import com.ctrlf.chat.dto.request.ChatSessionUpdateRequest;
import com.ctrlf.chat.dto.response.ChatSessionHistoryResponse;
import com.ctrlf.chat.dto.response.ChatSessionResponse;
import com.ctrlf.chat.service.ChatSessionService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 채팅 세션 관련 REST API 컨트롤러
 * 
 * <p>채팅 세션의 생성, 조회, 수정, 삭제 등의 기능을 제공합니다.</p>
 * <p>세션은 사용자와 AI 간의 대화를 그룹화하는 단위입니다.</p>
 * 
 * @author CtrlF Team
 * @since 1.0.0
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat/sessions")
public class ChatSessionController {

    private final ChatSessionService chatSessionService;

    /**
     * 세션 생성
     * 
     * <p>새로운 채팅 세션을 생성합니다.</p>
     * 
     * @param request 세션 생성 요청 (사용자 UUID, 제목, 도메인)
     * @return 생성된 세션 정보
     */
    @PostMapping
    public ChatSessionResponse create(@RequestBody ChatSessionCreateRequest request) {
        return chatSessionService.createSession(request);
    }

    /**
     * 세션 단건 조회
     * 
     * <p>특정 세션의 상세 정보를 조회합니다.</p>
     * 
     * @param sessionId 조회할 세션 ID
     * @return 세션 정보
     */
    @GetMapping("/{sessionId}")
    public ChatSessionResponse get(@PathVariable UUID sessionId) {
        return chatSessionService.getSession(sessionId);
    }

    /**
     * 세션 목록 조회
     * 
     * <p>모든 세션 목록을 조회합니다.</p>
     * 
     * @return 세션 목록
     */
    @GetMapping
    public List<ChatSessionResponse> getList() {
        return chatSessionService.getSessionList();
    }

    /**
     * 세션 수정
     * 
     * <p>세션의 제목 등의 정보를 수정합니다.</p>
     * 
     * @param sessionId 수정할 세션 ID
     * @param request 수정 요청 (제목 등)
     * @return 수정된 세션 정보
     */
    @PutMapping("/{sessionId}")
    public ChatSessionResponse update(
        @PathVariable UUID sessionId,
        @RequestBody ChatSessionUpdateRequest request
    ) {
        return chatSessionService.updateSession(sessionId, request);
    }

    /**
     * 세션 삭제 (Soft Delete)
     * 
     * <p>세션을 삭제합니다. 실제로는 deleted 플래그를 true로 설정하는 소프트 삭제입니다.</p>
     * 
     * @param sessionId 삭제할 세션 ID
     */
    @DeleteMapping("/{sessionId}")
    public void delete(@PathVariable UUID sessionId) {
        chatSessionService.deleteSession(sessionId);
    }

    /**
     * 세션 히스토리 조회
     * 
     * <p>세션의 전체 메시지 히스토리를 조회합니다.</p>
     * 
     * @param sessionId 조회할 세션 ID
     * @return 세션 정보 및 메시지 목록
     */
    @GetMapping("/{sessionId}/history")
    public ChatSessionHistoryResponse history(@PathVariable UUID sessionId) {
        return chatSessionService.getSessionHistory(sessionId);
    }
}
