package com.ctrlf.chat.faq.repository;

import com.ctrlf.chat.faq.entity.FaqCandidate;
import com.ctrlf.chat.faq.entity.FaqCandidate.CandidateStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FaqCandidateRepository
    extends JpaRepository<FaqCandidate, UUID> {

    // 도메인 + 상태로 후보 조회
    List<FaqCandidate> findByDomainAndStatus(
        String domain,
        CandidateStatus status
    );

    // 도메인 기준 후보 조회
    List<FaqCandidate> findByDomain(String domain);
}
