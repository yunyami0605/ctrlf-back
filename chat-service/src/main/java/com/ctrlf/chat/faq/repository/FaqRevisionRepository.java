package com.ctrlf.chat.faq.repository;

import com.ctrlf.chat.faq.entity.FaqRevision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FaqRevisionRepository extends JpaRepository<FaqRevision, UUID> {
}
