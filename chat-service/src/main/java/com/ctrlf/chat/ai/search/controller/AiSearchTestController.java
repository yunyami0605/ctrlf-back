//테스트용 controller
package com.ctrlf.chat.ai.search.controller;

import com.ctrlf.chat.ai.search.domain.SearchDataset;
import com.ctrlf.chat.ai.search.dto.AiSearchResponse;
import com.ctrlf.chat.ai.search.facade.SearchFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/ai")
public class AiSearchTestController {

    private final SearchFacade searchFacade;

    /**
     * ⚠ 테스트용 API (추후 삭제 가능)
     */
    @PostMapping("/search")
    public List<AiSearchResponse.Result> search(
        @RequestParam String query,
        @RequestParam(defaultValue = "policy") String dataset,
        @RequestParam(defaultValue = "5") int topK
    ) {
        return searchFacade.searchDocs(
            query,
            SearchDataset.from(dataset),
            topK
        );
    }
}
