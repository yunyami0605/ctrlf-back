package com.ctrlf.education.repository;

import com.ctrlf.education.entity.EducationScript;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EducationScriptRepository extends JpaRepository<EducationScript, UUID> {
}


