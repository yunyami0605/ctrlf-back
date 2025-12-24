package com.ctrlf.education.video.repository;

import com.ctrlf.education.video.entity.SourceSetDocument;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface SourceSetDocumentRepository extends JpaRepository<SourceSetDocument, UUID> {

    /**
     * 소스셋 ID로 포함된 문서 목록 조회.
     */
    @Query("SELECT ssd FROM SourceSetDocument ssd WHERE ssd.sourceSet.id = :sourceSetId")
    List<SourceSetDocument> findBySourceSetId(@Param("sourceSetId") UUID sourceSetId);

    /**
     * 소스셋 ID와 문서 ID로 조회.
     */
    @Query("SELECT ssd FROM SourceSetDocument ssd WHERE ssd.sourceSet.id = :sourceSetId AND ssd.documentId = :documentId")
    java.util.Optional<SourceSetDocument> findBySourceSetIdAndDocumentId(
        @Param("sourceSetId") UUID sourceSetId,
        @Param("documentId") UUID documentId
    );
}
