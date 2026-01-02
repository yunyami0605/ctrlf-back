package com.ctrlf.education.script.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ctrlf.education.script.entity.EducationScriptScene;

public interface EducationScriptSceneRepository extends JpaRepository<EducationScriptScene, UUID> {
    java.util.List<EducationScriptScene> findByChapterIdOrderBySceneIndexAsc(UUID chapterId);
    java.util.List<EducationScriptScene> findByScriptIdOrderByChapterIdAscSceneIndexAsc(UUID scriptId);

    /**
     * chapterId와 sceneIndex로 씬 조회.
     */
    Optional<EducationScriptScene> findByChapterIdAndSceneIndex(UUID chapterId, Integer sceneIndex);

    /**
     * 챕터의 씬 duration 합계 계산.
     */
    @Query("SELECT SUM(s.durationSec) FROM EducationScriptScene s WHERE s.chapterId = :chapterId")
    Integer sumDurationByChapterId(@Param("chapterId") UUID chapterId);
}

