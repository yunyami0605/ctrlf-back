package com.ctrlf.chat.ai.search.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ChatCompletionRequest {

    // ⚠️ FastAPI ChatRequest에 맞춰 snake_case 유지
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

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        private String role;     // "user" | "assistant"
        private String content;
    }
}
