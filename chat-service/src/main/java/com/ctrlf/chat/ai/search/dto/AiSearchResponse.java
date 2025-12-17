package com.ctrlf.chat.ai.search.dto;

import lombok.Data;
import java.util.List;

/**
 * AI 검색 응답 DTO
 */
@Data
public class AiSearchResponse {

    private List<Result> results;

    /**
     * 개별 검색 결과 정보
     */
    @Data
    public static class Result {
        private String docId;
        private String title;
        private Integer page;
        private Double score;
        private String snippet;
        private String dataset;
        private String source;
    }
}
