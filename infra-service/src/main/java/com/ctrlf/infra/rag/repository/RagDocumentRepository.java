package com.ctrlf.infra.rag.repository;

import com.ctrlf.infra.rag.entity.RagDocument;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * RAG 문서 저장소.
 * - 동적 필터 기반 목록 조회를 제공합니다.
 */
public interface RagDocumentRepository extends JpaRepository<RagDocument, UUID> {

    /**
     * 동적 조건 검색.
     * null 값은 무시되고, 제목 키워드는 부분 일치(lower like)로 검색합니다.
     */

    @Query("""
        select d
        from RagDocument d
        where (:domain is null or d.domain = :domain)
        and (:uploader is null or d.uploaderUuid = :uploader)
        and (:start is null or d.createdAt >= :start)
        and (:end is null or d.createdAt <= :end)
        order by d.createdAt desc
        """)
    Page<RagDocument> search(
        @Param("domain") String domain,
        @Param("uploader") String uploaderUuid,
        @Param("start") Instant startDate,
        @Param("end") Instant endDate,
        Pageable pageable
    );

    Page<RagDocument> findAllByDomainContainingIgnoreCaseAndUploaderUuidContainingIgnoreCaseAndTitleContainingIgnoreCaseAndCreatedAtBetween(
        String domain,
        String uploader,
        String keyword,
        Instant start,
        Instant end,
        Pageable pageable
    );

    // 사규 관리용 메서드
    List<RagDocument> findByDocumentIdOrderByVersionDesc(String documentId);
    
    List<RagDocument> findByDocumentId(String documentId);
    
    java.util.Optional<RagDocument> findByDocumentIdAndVersion(String documentId, Integer version);
    
    boolean existsByDocumentId(String documentId);
    
    @Query("""
        select d from RagDocument d
        where d.documentId is not null
        and (:search is null or lower(d.documentId) like lower(concat('%', :search, '%'))
             or lower(d.title) like lower(concat('%', :search, '%')))
        and (:status is null or d.status = :status)
        order by 
        case when d.status = 'ACTIVE' then 1
             when d.status = 'PENDING' then 2
             when d.status = 'DRAFT' then 3
             when d.status = 'ARCHIVED' then 4
             else 5 end,
        d.documentId, d.version desc
        """)
    List<RagDocument> findPolicies(
        @Param("search") String search,
        @Param("status") String status
    );
    
    @Query("""
        select d from RagDocument d
        where d.documentId is not null
        and d.status != 'ARCHIVED'
        and (:search is null or lower(d.documentId) like lower(concat('%', :search, '%'))
             or lower(d.title) like lower(concat('%', :search, '%')))
        and (:status is null or d.status = :status)
        order by 
        case when d.status = 'ACTIVE' then 1
             when d.status = 'PENDING' then 2
             when d.status = 'DRAFT' then 3
             else 4 end,
        d.documentId, d.version desc
        """)
    List<RagDocument> findPoliciesExcludingArchived(
        @Param("search") String search,
        @Param("status") String status
    );
}
