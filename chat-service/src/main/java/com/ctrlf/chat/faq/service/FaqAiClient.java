package com.ctrlf.chat.faq.service;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class FaqAiClient {

    private final RestClient restClient;

    public FaqAiClient(RestClient.Builder builder) {
        this.restClient = builder
            // AI Gateway base-url (필요 시 application.yml로 빼도 됨)
            .baseUrl("http://localhost:8000")
            .build();
    }

    public AiFaqResponse generate(
        String domain,
        String clusterId,
        String canonicalQuestion
    ) {
        return restClient.post()
            .uri("/ai/faq/generate")
            .body(new AiFaqRequest(domain, clusterId, canonicalQuestion))
            .retrieve()
            .body(AiFaqResponse.class);
    }

    /* ======================
       Request / Response DTO
       ====================== */

    public record AiFaqRequest(
        String domain,
        String cluster_id,
        String canonical_question
    ) {}

    public record AiFaqResponse(
        String status,
        FaqDraftPayload faq_draft,
        String error_message
    ) {}

    public record FaqDraftPayload(
        String faq_draft_id,
        String question,
        String answer_markdown,
        String summary,
        Double ai_confidence
    ) {}
}
