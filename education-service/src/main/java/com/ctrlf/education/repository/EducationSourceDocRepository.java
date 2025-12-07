package com.ctrlf.education.repository;

import com.ctrlf.education.entity.EducationSourceDoc;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EducationSourceDocRepository extends JpaRepository<EducationSourceDoc, UUID> {
}


