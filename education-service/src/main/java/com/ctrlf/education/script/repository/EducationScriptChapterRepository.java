package com.ctrlf.education.script.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ctrlf.education.script.entity.EducationScriptChapter;

public interface EducationScriptChapterRepository extends JpaRepository<EducationScriptChapter, UUID> {
    java.util.List<EducationScriptChapter> findByScriptIdOrderByChapterIndexAsc(UUID scriptId);

    /**
     * scriptId와 chapterIndex로 챕터 조회.
     */
    Optional<EducationScriptChapter> findByScriptIdAndChapterIndex(UUID scriptId, Integer chapterIndex);

    /**
     * 스크립트의 총 duration 합계 계산.
     */
    @Query("SELECT SUM(c.durationSec) FROM EducationScriptChapter c WHERE c.scriptId = :scriptId")
    Integer sumDurationByScriptId(@Param("scriptId") UUID scriptId);
}

