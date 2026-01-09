package com.ctrlf.chat.dto.response;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 채팅 로그 관련 DTO
 */
public final class ChatLogDtos {
    private ChatLogDtos() {}

    /**
     * 채팅 로그 항목
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatLogItem {
        private String id;
        private Instant createdAt;
        private String userId;
        private String userRole;
        private String department;
        private String domain;
        private String route;
        private String modelName;
        private String question;
        private String answer;
        private Boolean hasPiiInput;
        private Boolean hasPiiOutput;
        private Boolean ragUsed;
        private Integer ragSourceCount;
        private Long latencyMsTotal;
        private String errorCode;
        private String traceId;
        private String conversationId;
        private Integer turnId;
    }

    /**
     * 페이지 응답
     */
    @Getter
    @AllArgsConstructor
    public static class PageResponse<T> {
        private List<T> content;
        private Long totalElements;
        private Integer totalPages;
        private Integer page;
        private Integer size;
    }
}

