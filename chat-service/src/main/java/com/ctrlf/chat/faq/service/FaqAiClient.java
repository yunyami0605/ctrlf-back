package com.ctrlf.chat.faq.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
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
        String canonicalQuestion,
        List<String> sampleQuestions,
        List<TopDoc> topDocs
    ) {
        try {
            log.info("AI FAQ 생성 요청: domain={}, clusterId={}, question={}, sampleQuestionsCount={}, topDocsCount={}",
                domain, clusterId, canonicalQuestion,
                sampleQuestions != null ? sampleQuestions.size() : 0,
                topDocs != null ? topDocs.size() : 0);

            // AI 서버 엔드포인트: router prefix="/faq", endpoint="/generate"
            // FastAPI 앱에서 /ai prefix를 추가했다면 /ai/faq/generate가 됨
            // 다른 엔드포인트 패턴(/ai/chat/messages, /ai/chat/stream)을 참고하여 /ai/faq/generate 사용
            AiFaqResponse response = restClient.post()
                .uri("/ai/faq/generate")
                .body(new AiFaqRequest(domain, clusterId, canonicalQuestion, sampleQuestions, topDocs))
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), (req, res) -> {
                    String errorBody = "";
                    try {
                        errorBody = res.getBody().toString();
                    } catch (Exception e) {
                        // ignore
                    }
                    log.error("AI FAQ 생성 실패: status={}, domain={}, clusterId={}, errorBody={}",
                        res.getStatusCode(), domain, clusterId, errorBody);
                    throw new RestClientException(
                        String.format("AI 서비스 오류: HTTP %s - %s", res.getStatusCode(), errorBody)
                    );
                })
                .body(AiFaqResponse.class);

            log.info("AI FAQ 생성 응답: status={}, domain={}, clusterId={}",
                response.status(), domain, clusterId);

            return response;
        } catch (RestClientException e) {
            log.error("AI FAQ 생성 요청 실패: domain={}, clusterId={}, error={}",
                domain, clusterId, e.getMessage(), e);
            throw new IllegalStateException(
                String.format("AI 서비스 호출 실패: %s", e.getMessage()), e
            );
        }
    }

    /* ======================
       Request / Response DTO
       ====================== */

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AiFaqRequest(
        String domain,
        String cluster_id,
        String canonical_question,
        List<String> sample_questions,  // 샘플 질문 목록 (선택)
        List<TopDoc> top_docs  // RAG 검색 결과 (선택)
    ) {}

    /**
     * RAG 검색 결과 문서 정보
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TopDoc(
        String doc_id,          // 문서 ID
        String doc_version,       // 문서 버전 (선택)
        String title,             // 문서 제목
        String snippet,           // 검색된 텍스트 스니펫
        String article_label,     // 조항 라벨 (선택)
        String article_path,      // 조항 경로 (선택)
        Double score,             // 유사도 점수 (선택)
        Integer page,             // 페이지 번호 (선택)
        String dataset,           // 데이터셋 (선택)
        String source             // 출처 (선택)
    ) {}

    public record AiFaqResponse(
        String status,
        FaqDraftPayload faq_draft,
        String error_message
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FaqDraftPayload(
        String faq_draft_id,
        String domain,
        String cluster_id,
        String question,
        String answer_markdown,
        String summary,
        String source_doc_id,
        String source_doc_version,      // 문서 버전 (AI 서비스 응답)
        String source_article_label,
        String source_article_path,      // 조항 경로 (AI 서비스 응답)
        String answer_source,
        Double ai_confidence,
        String created_at
    ) {}
}
