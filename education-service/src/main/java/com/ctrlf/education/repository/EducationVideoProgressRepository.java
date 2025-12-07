package com.ctrlf.education.repository;

import com.ctrlf.education.entity.EducationVideoProgress;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EducationVideoProgressRepository extends JpaRepository<EducationVideoProgress, UUID> {
}


