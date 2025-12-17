package com.ctrlf.chat.faq.repository;

import com.ctrlf.chat.faq.entity.FaqDraft;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FaqDraftRepository extends JpaRepository<FaqDraft, UUID> {

    List<FaqDraft> findByDomainAndStatus(String domain, FaqDraft.Status status);

    List<FaqDraft> findByStatus(FaqDraft.Status status);

    List<FaqDraft> findByDomain(String domain);
}
