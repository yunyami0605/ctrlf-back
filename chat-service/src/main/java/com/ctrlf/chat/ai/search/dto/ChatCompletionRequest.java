package com.ctrlf.chat.ai.search.dto;

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
    private UUID session_id;
    private UUID user_id;
    private String user_role;
    private String department;
    private String domain;
    private String channel;
    private List<Message> messages;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        private String role;     // "user" | "assistant"
        private String content;
    }
}
