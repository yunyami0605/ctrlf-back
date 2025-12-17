package com.ctrlf.chat.ai.search.facade;

import com.ctrlf.chat.ai.search.domain.SearchDataset;
import com.ctrlf.chat.ai.search.dto.AiSearchResponse;
import com.ctrlf.chat.ai.search.service.AiSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 검색 결과를 동기 방식으로 제공하는 Facade
 */
@Service
@RequiredArgsConstructor
public class SearchFacade {

    private final AiSearchService aiSearchService;

    /**
     * 검색 결과 문서 리스트 조회
     */
    public List<AiSearchResponse.Result> searchDocs(
        String query,
        SearchDataset dataset,
        int topK
    ) {
        AiSearchResponse response = aiSearchService
            .search(query, dataset.getValue(), topK)
            .block();

        return response == null ? List.of() : response.getResults();
    }
}
