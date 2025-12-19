package com.ctrlf.chat.dto.response;

import com.ctrlf.chat.entity.ChatMessageStatus;
import java.time.Instant;
import java.util.UUID;

public record ChatMessageGetResponse(
    UUID id,
    UUID sessionId,
    String role,
    String content,
    ChatMessageStatus status,
    Instant createdAt
) {}
