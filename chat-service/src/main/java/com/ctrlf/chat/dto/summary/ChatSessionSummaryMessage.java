package com.ctrlf.chat.dto.summary;

import com.ctrlf.chat.entity.ChatMessage;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ChatSessionSummaryMessage {

    private String role;
    private String content;

    public static ChatSessionSummaryMessage from(ChatMessage message) {
        return new ChatSessionSummaryMessage(
            message.getRole(),
            message.getContent()
        );
    }
}
