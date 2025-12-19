package com.ctrlf.chat.ai.search.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChatCompletionResponse {

    private String answer;
    private Meta meta;

    @Getter
    @NoArgsConstructor
    public static class Meta {
        private String used_model;
        private String route;
        private Boolean masked;
        private Long latency_ms;
    }
}
