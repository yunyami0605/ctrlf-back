package com.ctrlf.chat.faq.dto.response;

import com.ctrlf.chat.faq.entity.FaqDraft;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FaqDraftResponse {

    private UUID id;
    private String domain;
    private String question;
    private String summary;
    private FaqDraft.Status status;
    private LocalDateTime createdAt;

    public static FaqDraftResponse from(FaqDraft draft) {
        return FaqDraftResponse.builder()
            .id(draft.getId())
            .domain(draft.getDomain())
            .question(draft.getQuestion())
            .summary(draft.getSummary())
            .status(draft.getStatus())
            .createdAt(draft.getCreatedAt())
            .build();
    }
}
