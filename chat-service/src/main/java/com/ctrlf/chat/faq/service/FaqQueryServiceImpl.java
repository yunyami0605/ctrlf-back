package com.ctrlf.chat.faq.service;

import com.ctrlf.chat.faq.dto.response.FaqDashboardResponse;
import com.ctrlf.chat.faq.repository.FaqRepository;
import java.util.List;
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
        return faqRepository.findHomeFaqs()
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
