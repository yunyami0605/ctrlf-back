package com.ctrlf.education.script.repository;

import com.ctrlf.education.script.entity.EducationScript;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 교육 스크립트 저장소.
 */
public interface EducationScriptRepository extends JpaRepository<EducationScript, UUID> {
    /** 교육 ID로 스크립트 목록 조회 (삭제되지 않은 것만) */
    List<EducationScript> findByEducationIdAndDeletedAtIsNullOrderByVersionDesc(UUID educationId);
    
    /** 교육 ID로 승인된 스크립트 목록 조회 (삭제되지 않고 승인된 것만, 버전 내림차순) */
    List<EducationScript> findByEducationIdAndDeletedAtIsNullAndStatusOrderByVersionDesc(UUID educationId, String status);
}


