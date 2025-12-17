package com.ctrlf.chat.faq.controller;

import com.ctrlf.chat.faq.dto.response.FaqDashboardResponse;
import com.ctrlf.chat.faq.service.FaqDashboardService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * FAQ 대시보드 조회 API
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/faq/dashboard")
public class FaqDashboardController {

    private final FaqDashboardService faqDashboardService;

    /**
     * 홈 화면용 FAQ (도메인별 1개)
     */
    @GetMapping("/home")
    public List<FaqDashboardResponse> getHomeFaqs() {
        return faqDashboardService.getHomeFaqs();
    }

    /**
     * 도메인별 TOP 10 FAQ
     */
    @GetMapping("/{domain}")
    public List<FaqDashboardResponse> getDomainFaqs(
        @PathVariable String domain
    ) {
        return faqDashboardService.getTopFaqsByDomain(domain);
    }
}
