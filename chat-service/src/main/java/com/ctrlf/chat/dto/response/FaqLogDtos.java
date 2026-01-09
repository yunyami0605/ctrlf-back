package com.ctrlf.chat.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * FAQ 로그 관련 DTO
 */
public final class FaqLogDtos {
    private FaqLogDtos() {}

    /**
     * FAQ 로그 항목 (FAQ 초안 생성용)
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FaqLogItem {
        private UUID id;
        private UUID sessionId;
        private String content;
        private String keyword;
        private String domain;
        private UUID userId;
        private Instant createdAt;
    }

    /**
     * FAQ 로그 목록 응답 (기존 AdminMessageLogResponse와 호환)
     */
    @Getter
    @AllArgsConstructor
    public static class FaqLogResponse {
        private List<FaqLogItem> messages;
        private Long totalCount;
    }
}

