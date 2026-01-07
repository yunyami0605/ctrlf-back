package com.ctrlf.chat.faq.service;

import com.ctrlf.chat.faq.dto.response.FaqDashboardResponse;
import com.ctrlf.chat.faq.entity.Faq;
import com.ctrlf.chat.faq.repository.FaqRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FaqQueryServiceImpl implements FaqQueryService {

    private final FaqRepository faqRepository;

    @Override
    public List<FaqDashboardResponse> getHomeFaqs() {
        // 모든 활성화된 FAQ를 도메인별, publishedAt DESC, priority ASC, createdAt ASC로 정렬하여 가져옴
        List<Faq> allFaqs = faqRepository.findAllActiveOrderedByDomainAndPublishedAt();
        
        log.info("[FAQ 조회] 홈 화면 FAQ 조회: 전체 활성화된 FAQ 개수={}, 도메인별 분포={}", 
            allFaqs.size(),
            allFaqs.stream()
                .collect(java.util.stream.Collectors.groupingBy(Faq::getDomain, java.util.stream.Collectors.counting())));
        
        // 도메인별로 첫 번째 FAQ만 선택 (LinkedHashMap으로 순서 유지)
        Map<String, Faq> domainFaqMap = new LinkedHashMap<>();
        for (Faq faq : allFaqs) {
            domainFaqMap.putIfAbsent(faq.getDomain(), faq);
        }
        
        List<FaqDashboardResponse> result = domainFaqMap.values()
            .stream()
            .map(FaqDashboardResponse::from)
            .toList();
        
        log.info("[FAQ 조회] 홈 화면 FAQ 반환: 도메인 개수={}, 도메인 목록={}", 
            result.size(),
            result.stream().map(FaqDashboardResponse::getDomain).toList());
        
        return result;
    }

    @Override
    public List<FaqDashboardResponse> getDomainFaqs(String domain) {
        log.info("[FAQ 조회] 도메인별 FAQ 조회 시작: domain={}", domain);
        
        // 도메인별 FAQ를 최대 2개로 제한
        List<Faq> faqs = faqRepository.findTop2ByDomainAndIsActiveTrueOrderByPublishedAtDesc(domain);
        
        log.info("[FAQ 조회] 도메인별 FAQ 조회 완료: domain={}, 조회된 FAQ 개수={}, FAQ IDs={}, 질문 목록={}", 
            domain, faqs.size(),
            faqs.stream().map(f -> f.getId().toString()).toList(),
            faqs.stream().map(f -> f.getQuestion().substring(0, Math.min(30, f.getQuestion().length()))).toList());
        
        return faqs.stream()
            .map(FaqDashboardResponse::from)
            .toList();
    }
}
