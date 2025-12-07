package com.ctrlf.education.repository;

import com.ctrlf.education.entity.EducationVideoReview;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EducationVideoReviewRepository extends JpaRepository<EducationVideoReview, UUID> {
}


