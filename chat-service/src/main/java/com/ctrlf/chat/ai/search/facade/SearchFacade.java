package com.ctrlf.chat.ai.search.facade;

import com.ctrlf.chat.ai.search.domain.SearchDataset;
import com.ctrlf.chat.ai.search.dto.AiSearchResponse;
import com.ctrlf.chat.ai.search.service.AiSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 검색 결과를 동기 방식으로 제공하는 Facade
 */
@Slf4j
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
        try {
            AiSearchResponse response = aiSearchService
                .search(query, dataset.getValue(), topK)
                .block();

            if (response == null) {
                log.warn("RAG 검색 응답이 null입니다. query={}, dataset={}", query, dataset);
                return List.of();
            }

            List<AiSearchResponse.Result> results = response.getResults();
            log.info("RAG 검색 성공: query={}, dataset={}, resultCount={}", query, dataset, results.size());
            return results;
        } catch (Exception e) {
            log.error("RAG 검색 실패: query={}, dataset={}, error={}", query, dataset, e.getMessage(), e);
            // RAG 검색 실패 시 빈 리스트 반환 (Draft 생성은 계속 진행)
            return List.of();
        }
    }
}
