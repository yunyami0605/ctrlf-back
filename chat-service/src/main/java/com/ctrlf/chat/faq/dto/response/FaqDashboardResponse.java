package com.ctrlf.chat.faq.dto.response;

import com.ctrlf.chat.faq.entity.Faq;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FaqDashboardResponse {

    private UUID id;
    private String domain;
    private String question;
    private String answer;
    private Instant publishedAt;

    public static FaqDashboardResponse from(Faq faq) {
        return FaqDashboardResponse.builder()
            .id(faq.getId())
            .domain(faq.getDomain())
            .question(faq.getQuestion())
            .answer(faq.getAnswer())
            .publishedAt(faq.getPublishedAt())
            .build();
    }
}
