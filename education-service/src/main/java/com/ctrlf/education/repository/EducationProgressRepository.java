package com.ctrlf.education.repository;

import com.ctrlf.education.entity.EducationProgress;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EducationProgressRepository extends JpaRepository<EducationProgress, UUID> {
}


