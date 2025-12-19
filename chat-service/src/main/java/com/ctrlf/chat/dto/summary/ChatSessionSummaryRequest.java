package com.ctrlf.chat.dto.summary;

import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ChatSessionSummaryRequest {

    private UUID sessionId;
    private List<ChatSessionSummaryMessage> messages;
    private int maxLength;
}
