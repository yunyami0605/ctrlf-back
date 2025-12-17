package com.ctrlf.chat.ai.search.dto;

import lombok.Data;

/**
 * AI 검색 요청 DTO
 */
@Data
public class AiSearchRequest {

    private String query;
    private Integer topK = 5;
    private String dataset;
}
