package com.ctrlf.education.repository;

import com.ctrlf.education.entity.EducationVideo;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EducationVideoRepository extends JpaRepository<EducationVideo, UUID> {
}


