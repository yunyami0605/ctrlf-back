package com.ctrlf.chat.faq.controller;

import com.ctrlf.chat.faq.dto.response.FaqDashboardResponse;
import com.ctrlf.chat.faq.service.FaqQueryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/faq")
public class FaqQueryController {

    private final FaqQueryService faqQueryService;

    /**
     * FAQ 홈 (도메인별 1개)
     */
    @GetMapping("/home")
    public List<FaqDashboardResponse> getHomeFaqs() {
        log.info("[FAQ 조회] 홈 화면 FAQ 조회 요청 수신: GET /faq/home");
        List<FaqDashboardResponse> result = faqQueryService.getHomeFaqs();
        log.info("[FAQ 조회] 홈 화면 FAQ 조회 응답 반환: FAQ 개수={}, 도메인 목록={}", 
            result.size(),
            result.stream().map(FaqDashboardResponse::getDomain).toList());
        return result;
    }

    /**
     * 도메인별 FAQ TOP 2 (도메인별 최대 2개로 제한)
     */
    @GetMapping
    public List<FaqDashboardResponse> getDomainFaqs(
        @RequestParam String domain
    ) {
        log.info("[FAQ 조회] 도메인별 FAQ 조회 요청 수신: GET /faq?domain={}", domain);
        List<FaqDashboardResponse> result = faqQueryService.getDomainFaqs(domain);
        log.info("[FAQ 조회] 도메인별 FAQ 조회 응답 반환: domain={}, FAQ 개수={}, FAQ IDs={}", 
            domain, result.size(),
            result.stream().map(FaqDashboardResponse::getId).toList());
        return result;
    }
}
