package com.ctrlf.chat.faq.service;

import com.ctrlf.chat.faq.dto.response.FaqDashboardResponse;
import com.ctrlf.chat.faq.entity.Faq;
import com.ctrlf.chat.faq.repository.FaqRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FaqQueryServiceImpl implements FaqQueryService {

    private final FaqRepository faqRepository;

    @Override
    public List<FaqDashboardResponse> getHomeFaqs() {
        // 모든 활성화된 FAQ를 도메인별, publishedAt DESC, priority ASC, createdAt ASC로 정렬하여 가져옴
        List<Faq> allFaqs = faqRepository.findAllActiveOrderedByDomainAndPublishedAt();
        
        // 도메인별로 첫 번째 FAQ만 선택 (LinkedHashMap으로 순서 유지)
        Map<String, Faq> domainFaqMap = new LinkedHashMap<>();
        for (Faq faq : allFaqs) {
            domainFaqMap.putIfAbsent(faq.getDomain(), faq);
        }
        
        return domainFaqMap.values()
            .stream()
            .map(FaqDashboardResponse::from)
            .toList();
    }

    @Override
    public List<FaqDashboardResponse> getDomainFaqs(String domain) {
        return faqRepository
            .findTop10ByDomainAndIsActiveTrueOrderByPublishedAtDesc(domain)
            .stream()
            .map(FaqDashboardResponse::from)
            .toList();
    }
}
