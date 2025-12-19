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
public class ChatAiRequest {

    @JsonProperty("session_id")
    private UUID sessionId;

    @JsonProperty("user_id")
    private UUID userId;

    @JsonProperty("user_role")
    private String userRole;

    @JsonProperty("department")
    private String department;

    @JsonProperty("domain")
    private String domain;

    @JsonProperty("channel")
    private String channel;

    @JsonProperty("messages")
    private List<ChatAiMessage> messages;
}
