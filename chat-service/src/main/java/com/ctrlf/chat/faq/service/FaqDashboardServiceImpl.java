package com.ctrlf.chat.faq.service;

import com.ctrlf.chat.faq.dto.response.FaqDashboardResponse;
import com.ctrlf.chat.faq.entity.Faq;
import com.ctrlf.chat.faq.repository.FaqRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FaqDashboardServiceImpl implements FaqDashboardService {

    private final FaqRepository faqRepository;

    /**
     * 홈 화면: 도메인별 1개 FAQ
     */
    @Override
    public List<FaqDashboardResponse> getHomeFaqs() {
        return faqRepository.findHomeFaqs()
            .stream()
            .map(FaqDashboardResponse::from)
            .toList();
    }

    /**
     * 도메인 상세: TOP 10 FAQ
     */
    @Override
    public List<FaqDashboardResponse> getTopFaqsByDomain(String domain) {
        return faqRepository.findTop10ByDomainAndIsActiveTrueOrderByPublishedAtDesc(domain)
            .stream()
            .map(FaqDashboardResponse::from)
            .toList();
    }
}
