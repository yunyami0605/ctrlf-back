package com.ctrlf.chat.ai.search.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ChatAiMessage {

    @JsonProperty("role")
    private String role;

    @JsonProperty("content")
    private String content;
}
