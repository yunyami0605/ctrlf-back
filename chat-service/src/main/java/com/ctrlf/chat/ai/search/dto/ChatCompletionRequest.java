package com.ctrlf.chat.ai.search.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChatCompletionRequest {

    // ⚠️ FastAPI ChatRequest에 맞춰 snake_case 유지
    @JsonProperty("request_id")
    private String request_id;  // 스트리밍 전용: 중복 방지 / 재시도용 고유 키 (선택)
    
    @JsonProperty("session_id")
    private UUID session_id;
    
    @JsonProperty("user_id")
    private UUID user_id;
    
    @JsonProperty("user_role")
    private String user_role;
    
    @JsonProperty("department")
    private String department;
    
    @JsonProperty("domain")
    private String domain;
    
    @JsonProperty("channel")
    private String channel;
    
    @JsonProperty("messages")
    private List<Message> messages;

    // A/B 테스트 임베딩 모델 선택 (선택)
    // "openai" (text-embedding-3-large) 또는 "sroberta" (ko-sroberta-multitask)
    // null인 경우 기본값(openai) 사용
    @JsonProperty("model")
    private String model;

    // 일반 채팅용 생성자 (request_id, model 없음) - 후방 호환성
    public ChatCompletionRequest(
        UUID session_id,
        UUID user_id,
        String user_role,
        String department,
        String domain,
        String channel,
        List<Message> messages
    ) {
        this.request_id = null;
        this.session_id = session_id;
        this.user_id = user_id;
        this.user_role = user_role;
        this.department = department;
        this.domain = domain;
        this.channel = channel;
        this.messages = messages;
        this.model = null;
    }

    // 스트리밍용 생성자 (request_id 포함, model 없음) - 후방 호환성
    public ChatCompletionRequest(
        String request_id,
        UUID session_id,
        UUID user_id,
        String user_role,
        String department,
        String domain,
        String channel,
        List<Message> messages
    ) {
        this.request_id = request_id;
        this.session_id = session_id;
        this.user_id = user_id;
        this.user_role = user_role;
        this.department = department;
        this.domain = domain;
        this.channel = channel;
        this.messages = messages;
        this.model = null;
    }

    // A/B 테스트용 생성자 (model 포함)
    public ChatCompletionRequest(
        String request_id,
        UUID session_id,
        UUID user_id,
        String user_role,
        String department,
        String domain,
        String channel,
        List<Message> messages,
        String model
    ) {
        this.request_id = request_id;
        this.session_id = session_id;
        this.user_id = user_id;
        this.user_role = user_role;
        this.department = department;
        this.domain = domain;
        this.channel = channel;
        this.messages = messages;
        this.model = model;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        private String role;     // "user" | "assistant"
        private String content;
    }
}
