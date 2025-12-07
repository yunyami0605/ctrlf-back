package com.ctrlf.education.repository;

import com.ctrlf.education.entity.VideoGenerationJob;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VideoGenerationJobRepository extends JpaRepository<VideoGenerationJob, UUID> {
}


