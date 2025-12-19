package com.ctrlf.chat.dto.request;

import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChatMessageCreateRequest {
    private String requestId;     // 필수 (중복 방지 키)
    private UUID sessionId;       // 필수(현재 DB 제약 때문에)
    private String userMessage;   // 필수
}
