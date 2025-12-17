package com.ctrlf.chat.faq.controller;

import com.ctrlf.chat.faq.dto.response.FaqDashboardResponse;
import com.ctrlf.chat.faq.service.FaqQueryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
        return faqQueryService.getHomeFaqs();
    }

    /**
     * 도메인별 FAQ TOP 10
     */
    @GetMapping
    public List<FaqDashboardResponse> getDomainFaqs(
        @RequestParam String domain
    ) {
        return faqQueryService.getDomainFaqs(domain);
    }
}
