package com.ctrlf.chat.faq.service;

import com.ctrlf.chat.faq.dto.response.FaqDashboardResponse;
import java.util.List;

public interface FaqDashboardService {

    // 홈 화면용 FAQ (도메인별 1개)
    List<FaqDashboardResponse> getHomeFaqs();

    // 도메인 상세 TOP 10
    List<FaqDashboardResponse> getTopFaqsByDomain(String domain);
}
