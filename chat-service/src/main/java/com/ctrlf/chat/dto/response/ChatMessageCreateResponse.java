package com.ctrlf.chat.dto.response;

import com.ctrlf.chat.entity.ChatMessageStatus;
import java.util.UUID;

public record ChatMessageCreateResponse(
    UUID messageId,
    ChatMessageStatus status
) {}
