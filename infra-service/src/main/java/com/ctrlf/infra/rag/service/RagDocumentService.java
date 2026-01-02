package com.ctrlf.infra.rag.service;

import static com.ctrlf.infra.rag.dto.RagDtos.*;

import com.ctrlf.infra.rag.entity.RagDocument;
import com.ctrlf.infra.rag.entity.RagDocumentChunk;
import com.ctrlf.infra.rag.entity.RagDocumentStatus;
import com.ctrlf.infra.rag.entity.RagFailChunk;
import com.ctrlf.infra.rag.client.RagAiClient;
import com.ctrlf.infra.rag.repository.RagDocumentChunkRepository;
import com.ctrlf.infra.rag.repository.RagFailChunkRepository;
import com.ctrlf.infra.rag.repository.RagDocumentRepository;
import com.ctrlf.infra.rag.repository.RagDocumentHistoryRepository;
import com.ctrlf.infra.rag.entity.RagDocumentHistory;
import com.ctrlf.infra.s3.service.S3Service;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
/**
 * RAG 문서 도메인 서비스.
 * - 업로드 메타 등록
 * - 문서 목록 검색(필터/페이징)
 *
 * 컨트롤러에서 받은 DTO를 받아 DB에 저장/조회하고,
 * 날짜 문자열 파싱 및 기본 검증을 수행합니다.
 */
public class RagDocumentService {
    private static final Logger log = LoggerFactory.getLogger(RagDocumentService.class);

    private final RagDocumentRepository documentRepository;
    private final RagDocumentChunkRepository chunkRepository;
    private final RagFailChunkRepository failChunkRepository;
    private final RagDocumentHistoryRepository historyRepository;
    private final RagAiClient ragAiClient;
    private final S3Service s3Service;

    /**
     * 문서 업로드 메타를 저장하고 초기 상태를 반환합니다.
     * @param req 업로드 요청 DTO(제목/도메인/파일URL)
     * @param uploaderUuid 업로더 UUID (JWT에서 추출)
     * @return 업로드 응답(문서ID, 상태=QUEUED, 생성시각)
     */
    public UploadResponse upload(UploadRequest req, UUID uploaderUuid) {
        RagDocument d = new RagDocument();
        d.setTitle(req.getTitle());
        d.setDomain(req.getDomain());
        d.setUploaderUuid(uploaderUuid.toString());
        d.setSourceUrl(req.getFileUrl());
        // DB 체크 제약 조건: QUEUED, PROCESSING, SUCCEEDED, FAILED, REPROCESSING만 허용
        d.setStatus(RagDocumentStatus.QUEUED);
        d.setCreatedAt(Instant.now());
        d = documentRepository.save(d);

        return new UploadResponse(
            d.getId().toString(),
            d.getStatus() != null ? d.getStatus().name() : RagDocumentStatus.QUEUED.name(),
            d.getCreatedAt().toString()
        );
    }

    public UpdateResponse update(String documentId, UpdateRequest req) {
        UUID id = parseUuid(documentId);
        RagDocument d = documentRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "document not found"));
        boolean changed = false;
        if (req.getTitle() != null && !req.getTitle().isBlank()) {
            d.setTitle(req.getTitle());
            changed = true;
        }
        if (req.getDomain() != null && !req.getDomain().isBlank()) {
            d.setDomain(req.getDomain());
            changed = true;
        }
        if (req.getFileUrl() != null && !req.getFileUrl().isBlank()) {
            d.setSourceUrl(req.getFileUrl());
            changed = true;
        }
        if (!changed) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "no fields to update");
        }
        documentRepository.save(d);
        String now = Instant.now().toString();
        // 변경사항이 있으면 AI 서버 재처리 요청 (베스트Effort)
        if (d.getSourceUrl() != null && !d.getSourceUrl().isBlank()) {
            try {
                ragAiClient.ingest(
                    d.getId(),
                    d.getDocumentId(),
                    d.getVersion(),
                    d.getSourceUrl(),
                    d.getDomain()
                );
            } catch (Exception e) {
                log.warn("AI 서버 재처리 요청 실패: id={}, documentId={}, error={}", 
                    d.getId(), d.getDocumentId(), e.getMessage());
            }
        }
        return new UpdateResponse(d.getId().toString(), "REPROCESSING", now);
    }

    public DeleteResponse delete(String documentId) {
        UUID id = parseUuid(documentId);
        RagDocument d = documentRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "document not found"));
        // delete children first
        chunkRepository.deleteByDocumentId(id);
        failChunkRepository.deleteByDocumentId(id);
        documentRepository.delete(d);
        return new DeleteResponse(id.toString(), "DELETED", Instant.now().toString());
    }

    public ReprocessResponse reprocess(String documentId, ReprocessRequest req) {
        UUID id = parseUuid(documentId);
        RagDocument d = documentRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "document not found"));
        boolean changed = false;
        if (req.getTitle() != null && !req.getTitle().isBlank()) {
            d.setTitle(req.getTitle());
            changed = true;
        }
        if (req.getDomain() != null && !req.getDomain().isBlank()) {
            d.setDomain(req.getDomain());
            changed = true;
        }
        if (req.getFileUrl() != null && !req.getFileUrl().isBlank()) {
            d.setSourceUrl(req.getFileUrl());
            changed = true;
        }
        if (changed) {
            documentRepository.save(d);
        }
        // AI 서버 처리 요청
        boolean received = true;
        String requestId = null;
        RagDocumentStatus status = RagDocumentStatus.REPROCESSING;
        if (d.getSourceUrl() != null && !d.getSourceUrl().isBlank()) {
            try {
                RagAiClient.AiResponse aiResp = ragAiClient.ingest(
                    d.getId(),
                    d.getDocumentId(),
                    d.getVersion(),
                    d.getSourceUrl(),
                    d.getDomain()
                );
                received = aiResp.isReceived();
                requestId = aiResp.getRequestId();
                if (aiResp.getStatus() != null) {
                    status = RagDocumentStatus.fromString(aiResp.getStatus());
                    if (status == null) {
                        status = RagDocumentStatus.REPROCESSING;
                    }
                }
                log.info("AI 서버 재처리 요청 성공: id={}, documentId={}, version={}, received={}, status={}, requestId={}, traceId={}", 
                    d.getId(), d.getDocumentId(), d.getVersion(), received, aiResp.getStatus(), 
                    aiResp.getRequestId(), aiResp.getTraceId());
            } catch (Exception e) {
                log.warn("AI 서버 재처리 요청 실패: id={}, documentId={}, error={}", 
                    d.getId(), d.getDocumentId(), e.getMessage());
            }
        }
        // ReprocessResponse는 기존 API 스펙 유지 (accepted, jobId 필드)
        // received → accepted, requestId → jobId로 매핑
        return new ReprocessResponse(id.toString(), received, status.name(), 
            requestId != null ? requestId : "unknown", Instant.now().toString());
    }

    /**
     * 문서 목록을 필터/페이징으로 조회합니다.
     *
     * @param domain 도메인 필터(선택)
     * @param uploaderUuid 업로더 UUID(선택)
     * @param startDate yyyy-MM-dd(선택)
     * @param endDate yyyy-MM-dd(선택)
     * @param keyword 제목 키워드(선택)
     * @param page 페이지 번호
     * @param size 페이지 크기
     * @return DocumentListItem 리스트
     */
    public List<DocumentListItem> list(
        String domain,
        String uploaderUuid,
        String startDate,
        String endDate,
        String keyword,
        int page,
        int size
    ) {
        Instant start = null;
        Instant end = null;
        try {
            if (startDate != null && !startDate.isBlank()) {
                start = LocalDate.parse(startDate).atStartOfDay().toInstant(java.time.ZoneOffset.UTC);
            }
            if (endDate != null && !endDate.isBlank()) {
                end = LocalDate.parse(endDate).plusDays(1).atStartOfDay().toInstant(java.time.ZoneOffset.UTC);
            }
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid date format (yyyy-MM-dd)");
        }
        Pageable pageable = PageRequest.of(page, size);

        Page<RagDocument> pageRes =
            documentRepository.findAllByDomainContainingIgnoreCaseAndUploaderUuidContainingIgnoreCaseAndTitleContainingIgnoreCaseAndCreatedAtBetween(
                domain == null ? "" : domain,
                uploaderUuid == null ? "" : uploaderUuid,
                keyword == null ? "" : keyword,
                start == null ? Instant.EPOCH : start,
                end == null ? Instant.now() : end,
                pageable
            );


        List<DocumentListItem> list = new ArrayList<>();
        for (RagDocument d : pageRes.getContent()) {
            list.add(new DocumentListItem(
                d.getId().toString(),
                d.getTitle(),
                d.getDomain(),
                d.getUploaderUuid(),
                d.getCreatedAt() != null ? d.getCreatedAt().toString() : null
            ));
        }
        return list;
    }

    /**
     * 문서 정보를 조회합니다.
     */
    public DocumentInfoResponse getDocument(String documentId) {
        UUID id = parseUuid(documentId);
        RagDocument d = documentRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "document not found"));
        
        return new DocumentInfoResponse(
            d.getId().toString(),
            d.getTitle(),
            d.getDomain(),
            d.getSourceUrl(),
            d.getStatus() != null ? d.getStatus().name() : RagDocumentStatus.QUEUED.name()
        );
    }

    /**
     * 문서의 임베딩 처리 상태를 조회합니다.
     */
    public DocumentStatusResponse getStatus(String documentId) {
        UUID id = parseUuid(documentId);
        RagDocument d = documentRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "document not found"));
        
        return new DocumentStatusResponse(
            d.getId().toString(),
            d.getStatus() != null ? d.getStatus().name() : RagDocumentStatus.QUEUED.name(),
            d.getCreatedAt() != null ? d.getCreatedAt().toString() : null,
            d.getProcessedAt() != null ? d.getProcessedAt().toString() : null
        );
    }

    /**
     * 문서의 원문 텍스트를 조회합니다.
     * S3에서 파일을 다운로드하여 텍스트를 추출합니다.
     * 
     * <p>참고: 현재는 텍스트 파일(.txt)만 지원합니다.
     * PDF 등 다른 형식은 추후 확장 필요.
     */
    public DocumentTextResponse getText(String documentId) {
        UUID id = parseUuid(documentId);
        RagDocument doc = documentRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "document not found"));
        
        if (doc.getSourceUrl() == null || doc.getSourceUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "source URL not found");
        }

        try {
            // S3 Presigned URL 생성
            URL downloadUrl = s3Service.presignDownload(doc.getSourceUrl());
            
            // 파일 다운로드 및 텍스트 추출
            String text = extractTextFromUrl(downloadUrl, doc.getSourceUrl());
            
            return new DocumentTextResponse(doc.getId().toString(), text);
            
        } catch (Exception e) {
            log.error("텍스트 추출 실패. documentId={}, sourceUrl={}, error={}", 
                documentId, doc.getSourceUrl(), e.getMessage(), e);
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "텍스트 추출 실패: " + e.getMessage());
        }
    }

    /**
     * URL에서 텍스트를 추출합니다.
     * 
     * <p>현재는 텍스트 파일만 지원합니다.
     * PDF 등 다른 형식은 추후 확장 필요 (Apache Tika, PDFBox 등 사용).
     */
    private String extractTextFromUrl(URL url, String sourceUrl) throws Exception {
        // 파일 확장자 확인
        String lowerUrl = sourceUrl.toLowerCase();
        
        if (lowerUrl.endsWith(".txt") || lowerUrl.endsWith(".text")) {
            // 텍스트 파일: 직접 읽기
            try (InputStream is = url.openStream();
                 BufferedReader reader = new BufferedReader(
                     new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } else if (lowerUrl.endsWith(".pdf")) {
            // PDF 파일: TODO - PDF 추출 라이브러리 필요 (Apache PDFBox 등)
            throw new UnsupportedOperationException(
                "PDF 텍스트 추출은 아직 지원되지 않습니다. 텍스트 파일(.txt)만 지원됩니다.");
        } else {
            // 기타: 텍스트로 시도
            log.warn("알 수 없는 파일 형식. 텍스트로 시도합니다. sourceUrl={}", sourceUrl);
            try (InputStream is = url.openStream();
                 BufferedReader reader = new BufferedReader(
                     new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        }
    }

    /**
     * 문서 청크 Bulk Upsert (내부 API - FastAPI → Spring).
     * 
     * @param documentId 문서 ID
     * @param req 청크 bulk upsert 요청
     * @return 저장 결과
     */
    @org.springframework.transaction.annotation.Transactional
    public ChunksBulkUpsertResponse bulkUpsertChunks(String documentId, ChunksBulkUpsertRequest req) {
        try {
        UUID docId = parseUuid(documentId);
            log.info("청크 bulk upsert 시작: documentId={}, chunksCount={}", documentId, 
                req.getChunks() != null ? req.getChunks().size() : 0);
        
        // 문서 존재 확인
        if (!documentRepository.existsById(docId)) {
                log.error("문서를 찾을 수 없음: documentId={}", documentId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "document not found: " + documentId);
        }

        // 기존 청크 삭제 (재적재 시)
        chunkRepository.deleteByDocumentId(docId);
            log.debug("기존 청크 삭제 완료: documentId={}", documentId);

            // 입력값 검증 및 새 청크 저장
            if (req.getChunks() == null || req.getChunks().isEmpty()) {
                log.warn("청크 리스트가 비어있음: documentId={}", documentId);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "chunks list is empty");
            }

        List<RagDocumentChunk> chunks = new ArrayList<>();
            for (int i = 0; i < req.getChunks().size(); i++) {
                ChunkItem item = req.getChunks().get(i);
                
                // 입력값 검증
                if (item.getChunkIndex() == null) {
                    log.error("청크 인덱스가 null: documentId={}, chunkIndex={}", documentId, i);
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                        "chunkIndex is null at index " + i);
                }
                if (item.getChunkText() == null || item.getChunkText().isBlank()) {
                    log.error("청크 텍스트가 null 또는 빈 문자열: documentId={}, chunkIndex={}", 
                        documentId, item.getChunkIndex());
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                        "chunkText is null or blank at chunkIndex " + item.getChunkIndex());
                }
                
            RagDocumentChunk chunk = new RagDocumentChunk();
            chunk.setDocumentId(docId);
            chunk.setChunkIndex(item.getChunkIndex());
            chunk.setChunkText(item.getChunkText());
            chunk.setCreatedAt(Instant.now());
            // embedding은 null (Milvus에 저장됨)
            // chunkMeta는 현재 DB 스키마에 없으므로 저장하지 않음 (추후 추가 가능)
            chunks.add(chunk);
        }
        
            log.debug("청크 저장 시도: documentId={}, count={}", documentId, chunks.size());
        chunkRepository.saveAll(chunks);
        
        log.info("청크 bulk upsert 완료: documentId={}, count={}", documentId, chunks.size());
        
        return new ChunksBulkUpsertResponse(true, chunks.size());
        } catch (ResponseStatusException e) {
            // 이미 처리된 예외는 그대로 전파
            throw e;
        } catch (Exception e) {
            log.error("청크 bulk upsert 실패: documentId={}, error={}", documentId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                "Failed to save chunks: " + e.getMessage(), e);
        }
    }

    /**
     * 임베딩 실패 로그 Bulk Upsert (내부 API - FastAPI → Spring).
     * 
     * @param documentId 문서 ID
     * @param req 실패 청크 bulk upsert 요청
     * @return 저장 결과
     */
    @org.springframework.transaction.annotation.Transactional
    public FailChunksBulkUpsertResponse bulkUpsertFailChunks(String documentId, FailChunksBulkUpsertRequest req) {
        UUID docId = parseUuid(documentId);
        
        // 문서 존재 확인
        if (!documentRepository.existsById(docId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "document not found: " + documentId);
        }

        // 실패 청크 저장 (멱등: 같은 chunkIndex가 있으면 업데이트)
        List<RagFailChunk> failChunks = new ArrayList<>();
        for (FailChunkItem item : req.getFails()) {
            // 기존 실패 로그가 있으면 삭제 (멱등 처리)
            failChunkRepository.findByDocumentIdAndChunkIndex(docId, item.getChunkIndex())
                .ifPresent(failChunkRepository::delete);
            
            RagFailChunk failChunk = new RagFailChunk();
            failChunk.setDocumentId(docId);
            failChunk.setChunkIndex(item.getChunkIndex());
            failChunk.setFailReason(item.getFailReason());
            failChunk.setCreatedAt(Instant.now());
            failChunks.add(failChunk);
        }
        
        failChunkRepository.saveAll(failChunks);
        
        log.info("실패 청크 bulk upsert 완료: documentId={}, count={}", documentId, failChunks.size());
        
        return new FailChunksBulkUpsertResponse(true, failChunks.size());
    }

    private static UUID parseUuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid uuid");
        }
    }

    /**
     * RagDocument를 VersionDetail로 변환하는 헬퍼 메서드
     */
    private VersionDetail mapToVersionDetail(RagDocument doc) {
        return new VersionDetail(
            doc.getId().toString(),
            doc.getDocumentId(),
            doc.getTitle(),
            doc.getDomain(),
            doc.getVersion(),
            doc.getStatus() != null ? doc.getStatus().name() : null,
            doc.getChangeSummary(),
            doc.getSourceUrl(),
            doc.getUploaderUuid(),
            doc.getCreatedAt() != null ? doc.getCreatedAt().toString() : null,
            doc.getProcessedAt() != null ? doc.getProcessedAt().toString() : null,
            doc.getPreprocessStatus() != null ? doc.getPreprocessStatus() : "IDLE",
            doc.getPreprocessPages(),
            doc.getPreprocessChars(),
            doc.getPreprocessExcerpt(),
            doc.getPreprocessError(),
            doc.getReviewRequestedAt() != null ? doc.getReviewRequestedAt().toString() : null,
            doc.getReviewItemId()
        );
    }

    // ========== Policy Management Methods ==========

    /**
     * 사규 목록 조회 (document_id별 그룹화, 페이지네이션 지원)
     * 기본적으로 ARCHIVED 상태는 제외됩니다.
     * ARCHIVED를 조회하려면 status=ARCHIVED로 명시적으로 지정하세요.
     */
    public com.ctrlf.common.dto.PageResponse<PolicyListItem> listPolicies(
        String search, 
        String status, 
        Pageable pageable
    ) {
        List<RagDocument> documents;
        
        // String을 enum으로 변환
        RagDocumentStatus statusEnum = null;
        if (status != null && !status.isBlank() && !"전체".equals(status)) {
            statusEnum = RagDocumentStatus.fromString(status);
        }
        
        // status가 ARCHIVED인 경우에만 findPolicies 사용 (ARCHIVED 포함)
        // 그 외에는 기본적으로 ARCHIVED 제외
        if (statusEnum == RagDocumentStatus.ARCHIVED) {
            documents = documentRepository.findPolicies(statusEnum);
        } else {
            // 기본적으로 ARCHIVED 제외
            documents = documentRepository.findPoliciesExcludingArchived(statusEnum);
        }

        // 검색 필터링 (애플리케이션 레벨)
        if (search != null && !search.isBlank()) {
            String searchLower = search.toLowerCase();
            documents = documents.stream()
                .filter(doc -> {
                    String docId = doc.getDocumentId();
                    String title = doc.getTitle();
                    return (docId != null && docId.toLowerCase().contains(searchLower)) ||
                           (title != null && title.toLowerCase().contains(searchLower));
                })
                .collect(java.util.stream.Collectors.toList());
        }

        // document_id별로 그룹화
        java.util.Map<String, List<RagDocument>> grouped = documents.stream()
            .collect(java.util.stream.Collectors.groupingBy(RagDocument::getDocumentId));

        List<PolicyListItem> result = new ArrayList<>();
        for (java.util.Map.Entry<String, List<RagDocument>> entry : grouped.entrySet()) {
            String docId = entry.getKey();
            List<RagDocument> versions = entry.getValue();
            
            // 첫 번째 문서의 제목과 도메인 사용
            RagDocument first = versions.get(0);
            
            List<VersionSummary> versionSummaries = versions.stream()
                .map(v -> new VersionSummary(
                    v.getVersion(),
                    v.getStatus() != null ? v.getStatus().name() : null,
                    v.getCreatedAt() != null ? v.getCreatedAt().toString() : null
                ))
                .collect(java.util.stream.Collectors.toList());
            
            result.add(new PolicyListItem(
                first.getId().toString(),  // 첫 번째 버전의 UUID (PK)
                docId,
                first.getTitle(),
                first.getDomain(),
                versionSummaries,
                versions.size()
            ));
        }
        
        // 페이지네이션 적용
        int total = result.size();
        int page = pageable.getPageNumber();
        int size = pageable.getPageSize();
        int start = page * size;
        int end = Math.min(start + size, total);
        
        List<PolicyListItem> pagedResult = start < total 
            ? result.subList(start, end) 
            : new ArrayList<>();
        
        return new com.ctrlf.common.dto.PageResponse<>(
            pagedResult,
            page,
            size,
            total
        );
    }

    /**
     * 사규 상세 조회 (document_id 기준, 모든 버전)
     */
    public PolicyDetailResponse getPolicy(String documentId) {
        List<RagDocument> versions = documentRepository.findByDocumentIdOrderByVersionDesc(documentId);
        if (versions.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Policy not found: " + documentId);
        }
        
        RagDocument first = versions.get(0);
        List<VersionDetail> versionDetails = versions.stream()
            .map(v -> mapToVersionDetail(v))
            .collect(java.util.stream.Collectors.toList());
        
        return new PolicyDetailResponse(
            documentId,
            first.getTitle(),
            first.getDomain(),
            versionDetails
        );
    }

    /**
     * 버전별 상세 조회
     */
    public VersionDetail getPolicyVersion(String documentId, Integer version) {
        RagDocument doc = documentRepository.findByDocumentIdAndVersion(documentId, version)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                "Policy version not found: " + documentId + " v" + version));
        
        return mapToVersionDetail(doc);
    }

    /**
     * 버전 목록 조회
     */
    public List<VersionDetail> getPolicyVersions(String documentId) {
        List<RagDocument> versions = documentRepository.findByDocumentIdOrderByVersionDesc(documentId);
        if (versions.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Policy not found: " + documentId);
        }
        
        return versions.stream()
            .map(v -> mapToVersionDetail(v))
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 새 사규 생성
     */
    public CreatePolicyResponse createPolicy(CreatePolicyRequest req, UUID uploaderUuid) {
        if (documentRepository.existsByDocumentId(req.getDocumentId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, 
                "Policy already exists: " + req.getDocumentId());
        }
        
        RagDocument doc = new RagDocument();
        doc.setDocumentId(req.getDocumentId());
        doc.setTitle(req.getTitle());
        doc.setDomain(req.getDomain());
        doc.setVersion(1);
        doc.setStatus(RagDocumentStatus.DRAFT);
        doc.setChangeSummary(req.getChangeSummary());
        doc.setSourceUrl(req.getFileUrl());
        doc.setUploaderUuid(uploaderUuid.toString());
        doc.setCreatedAt(Instant.now());
        
        doc = documentRepository.save(doc);
        
        // AI 서버에 처리 요청 (파일이 있는 경우에만)
        if (req.getFileUrl() != null && !req.getFileUrl().isBlank()) {
            try {
                // AI 서버에 문서 임베딩 처리 요청
                // AI 서버는 처리 완료 후 PATCH /internal/rag/documents/{ragDocumentPk}/status 로 콜백을 보냅니다
                RagAiClient.AiResponse aiResp = ragAiClient.ingest(
                    doc.getId(),  // UUID (AI 서버가 콜백 시 사용할 PK)
                    doc.getDocumentId(),
                    doc.getVersion(),
                    doc.getSourceUrl(),
                    doc.getDomain()
                );
                log.info("AI 서버 처리 요청 성공: id={}, documentId={}, version={}, received={}, status={}, requestId={}, traceId={}", 
                    doc.getId(), doc.getDocumentId(), doc.getVersion(), aiResp.isReceived(), aiResp.getStatus(), 
                    aiResp.getRequestId(), aiResp.getTraceId());

                // AI 서버 응답의 status를 enum으로 변환하여 설정
                if (aiResp.getStatus() != null) {
                    RagDocumentStatus status = RagDocumentStatus.fromString(aiResp.getStatus());
                    if (status != null) {
                        doc.setStatus(status);
                    } else {
                        // 변환 실패 시 기본값 유지 (DRAFT)
                        log.warn("AI 서버 응답의 status를 변환할 수 없음: status={}, documentId={}", 
                            aiResp.getStatus(), doc.getDocumentId());
                    }
                }
            } catch (Exception e) {
                log.error("AI 서버 처리 요청 실패: id={}, documentId={}, version={}, error={}", 
                    doc.getId(), doc.getDocumentId(), doc.getVersion(), e.getMessage(), e);
                // AI 서버 호출 실패해도 사규 생성은 성공으로 처리 (비동기 재시도 가능)
            }
        }
        
        return new CreatePolicyResponse(
            doc.getId().toString(),
            doc.getDocumentId(),
            doc.getTitle(),
            doc.getVersion(),
            doc.getStatus() != null ? doc.getStatus().name() : RagDocumentStatus.DRAFT.name(),
            doc.getCreatedAt().toString()
        );
    }

    /**
     * 새 버전 생성
     */
    public CreateVersionResponse createVersion(String documentId, CreateVersionRequest req, UUID uploaderUuid) {
        // 기존 버전들 조회하여 최신 버전 확인
        List<RagDocument> existingVersions = documentRepository.findByDocumentIdOrderByVersionDesc(documentId);
        if (existingVersions.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Policy not found: " + documentId);
        }
        
        RagDocument latest = existingVersions.get(0);
        int newVersion = latest.getVersion() != null ? latest.getVersion() + 1 : 1;
        
        RagDocument newVersionDoc = new RagDocument();
        newVersionDoc.setDocumentId(documentId);
        newVersionDoc.setTitle(latest.getTitle());
        newVersionDoc.setDomain(latest.getDomain());
        newVersionDoc.setVersion(newVersion);
        newVersionDoc.setStatus(RagDocumentStatus.DRAFT);
        newVersionDoc.setChangeSummary(req.getChangeSummary());
        newVersionDoc.setSourceUrl(req.getFileUrl());
        newVersionDoc.setUploaderUuid(uploaderUuid.toString());
        newVersionDoc.setCreatedAt(Instant.now());
        
        newVersionDoc = documentRepository.save(newVersionDoc);
        
        // AI 서버에 처리 요청 (파일이 있는 경우에만)
        if (req.getFileUrl() != null && !req.getFileUrl().isBlank()) {
            try {
                // AI 서버에 문서 임베딩 처리 요청
                // AI 서버는 처리 완료 후 PATCH /internal/rag/documents/{ragDocumentPk}/status 로 콜백을 보냅니다
                RagAiClient.AiResponse aiResp = ragAiClient.ingest(
                    newVersionDoc.getId(),  // UUID (AI 서버가 콜백 시 사용할 PK)
                    newVersionDoc.getDocumentId(),
                    newVersionDoc.getVersion(),
                    newVersionDoc.getSourceUrl(),
                    newVersionDoc.getDomain()
                );
                log.info("AI 서버 처리 요청 성공: id={}, documentId={}, version={}, received={}, status={}, requestId={}, traceId={}", 
                    newVersionDoc.getId(), newVersionDoc.getDocumentId(), newVersionDoc.getVersion(), 
                    aiResp.isReceived(), aiResp.getStatus(), aiResp.getRequestId(), aiResp.getTraceId());
            } catch (Exception e) {
                log.error("AI 서버 처리 요청 실패: id={}, documentId={}, version={}, error={}", 
                    newVersionDoc.getId(), newVersionDoc.getDocumentId(), newVersionDoc.getVersion(), 
                    e.getMessage(), e);
                // AI 서버 호출 실패해도 버전 생성은 성공으로 처리 (비동기 재시도 가능)
            }
        }
        
        return new CreateVersionResponse(
            newVersionDoc.getId().toString(),
            newVersionDoc.getDocumentId(),
            newVersionDoc.getVersion(),
            newVersionDoc.getStatus() != null ? newVersionDoc.getStatus().name() : RagDocumentStatus.DRAFT.name(),
            newVersionDoc.getCreatedAt().toString()
        );
    }

    /**
     * 버전 수정
     */
    public UpdateVersionResponse updateVersion(String documentId, Integer version, UpdateVersionRequest req) {
        RagDocument doc = documentRepository.findByDocumentIdAndVersion(documentId, version)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                "Policy version not found: " + documentId + " v" + version));
        
        boolean changed = false;
        if (req.getTitle() != null && !req.getTitle().isBlank()) {
            doc.setTitle(req.getTitle());
            changed = true;
        }
        if (req.getChangeSummary() != null && !req.getChangeSummary().isBlank()) {
            doc.setChangeSummary(req.getChangeSummary());
            changed = true;
        }
        if (req.getFileUrl() != null && !req.getFileUrl().isBlank()) {
            doc.setSourceUrl(req.getFileUrl());
            changed = true;
        }
        
        if (!changed) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "no fields to update");
        }
        
        doc = documentRepository.save(doc);
        
        return new UpdateVersionResponse(
            doc.getId().toString(),
            doc.getDocumentId(),
            doc.getVersion(),
            doc.getStatus() != null ? doc.getStatus().name() : null,
            Instant.now().toString()
        );
    }

    /**
     * 상태 변경
     */
    public UpdateStatusResponse updateStatus(String documentId, Integer version, UpdateStatusRequest req) {
        RagDocument doc = documentRepository.findByDocumentIdAndVersion(documentId, version)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                "Policy version not found: " + documentId + " v" + version));
        
        String newStatusStr = req.getStatus();
        RagDocumentStatus newStatus = RagDocumentStatus.fromString(newStatusStr);
        if (newStatus == null || !newStatus.isPolicyManagementStatus()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "Invalid status: " + newStatusStr + ". Allowed: ACTIVE, DRAFT, PENDING, ARCHIVED");
        }
        
        // ACTIVE로 변경 시, 같은 document_id의 다른 ACTIVE 버전들을 DRAFT로 변경
        if (newStatus == RagDocumentStatus.ACTIVE) {
            List<RagDocument> activeVersions = documentRepository.findByDocumentId(documentId).stream()
                .filter(v -> v.getStatus() == RagDocumentStatus.ACTIVE && !v.getVersion().equals(version))
                .collect(java.util.stream.Collectors.toList());
            
            for (RagDocument active : activeVersions) {
                active.setStatus(RagDocumentStatus.DRAFT);
                documentRepository.save(active);
            }
        }
        
        RagDocumentStatus oldStatus = doc.getStatus();
        doc.setStatus(newStatus);
        
        // PENDING으로 변경 시 검토 요청 시각 기록
        if (newStatus == RagDocumentStatus.PENDING) {
            doc.setReviewRequestedAt(Instant.now());
            // 검토 항목 ID 생성 (예: "REVIEW-" + documentId + "-v" + version)
            doc.setReviewItemId("REVIEW-" + documentId + "-v" + version);
        }
        
        doc = documentRepository.save(doc);
        
        // 히스토리 기록
        String action = "STATUS_CHANGED";
        String message = String.format("상태 변경: %s → %s", 
            oldStatus != null ? oldStatus.name() : "null", 
            newStatus.name());
        addHistory(documentId, version, action, null, message);
        
        return new UpdateStatusResponse(
            doc.getId().toString(),
            doc.getDocumentId(),
            doc.getVersion(),
            doc.getStatus() != null ? doc.getStatus().name() : null,
            Instant.now().toString()
        );
    }

    /**
     * 파일 업로드/교체
     */
    public ReplaceFileResponse replaceFile(String documentId, Integer version, ReplaceFileRequest req) {
        RagDocument doc = documentRepository.findByDocumentIdAndVersion(documentId, version)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                "Policy version not found: " + documentId + " v" + version));
        
        doc.setSourceUrl(req.getFileUrl());
        // 파일 교체 시 전처리 상태 초기화
        doc.setPreprocessStatus("PROCESSING");
        doc.setPreprocessError(null);
        doc = documentRepository.save(doc);
        
        // AI 서버에 전처리 요청 (파일이 있는 경우에만)
        if (req.getFileUrl() != null && !req.getFileUrl().isBlank()) {
            try {
                // AI 서버에 문서 임베딩 처리 요청
                // AI 서버는 처리 완료 후 PATCH /internal/rag/documents/{ragDocumentPk}/status 로 콜백을 보냅니다
                RagAiClient.AiResponse aiResp = ragAiClient.ingest(
                    doc.getId(),  // UUID (AI 서버가 콜백 시 사용할 PK)
                    doc.getDocumentId(),
                    doc.getVersion(),
                    doc.getSourceUrl(),
                    doc.getDomain()
                );
                log.info("AI 서버 처리 요청 성공: id={}, documentId={}, version={}, received={}, status={}, requestId={}, traceId={}", 
                    doc.getId(), doc.getDocumentId(), doc.getVersion(), aiResp.isReceived(), aiResp.getStatus(), 
                    aiResp.getRequestId(), aiResp.getTraceId());
            } catch (Exception e) {
                log.error("AI 서버 처리 요청 실패: id={}, documentId={}, version={}, error={}", 
                    doc.getId(), doc.getDocumentId(), doc.getVersion(), e.getMessage(), e);
                // AI 서버 호출 실패 시 전처리 상태를 FAILED로 설정
                doc.setPreprocessStatus("FAILED");
                doc.setPreprocessError("AI 서버 처리 요청 실패: " + e.getMessage());
                documentRepository.save(doc);
            }
        }
        
        return new ReplaceFileResponse(
            doc.getId().toString(),
            doc.getDocumentId(),
            doc.getVersion(),
            doc.getSourceUrl(),
            Instant.now().toString()
        );
    }

    /**
     * 사규 상태 업데이트 (내부 API - AI → Backend).
     * AI 서버가 임베딩 처리를 완료한 후 콜백으로 호출합니다.
     * ragDocumentPk(UUID)로 문서를 찾아 상태를 업데이트합니다.
     */
    public InternalUpdateStatusResponse updateDocumentStatus(UUID ragDocumentPk, InternalUpdateStatusRequest req) {
        RagDocument doc = documentRepository.findById(ragDocumentPk)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                "Document not found: " + ragDocumentPk));

        // documentId 검증 (AI 서버가 보낸 값과 일치하는지 확인)
        if (req.getDocumentId() != null && !req.getDocumentId().isBlank()) {
            if (!req.getDocumentId().equals(doc.getDocumentId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    "DocumentId mismatch: expected " + doc.getDocumentId() + ", got " + req.getDocumentId());
            }
        }

        // version 검증 (AI 서버가 보낸 값과 일치하는지 확인)
        if (req.getVersion() != null) {
            if (!req.getVersion().equals(doc.getVersion())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    "Version mismatch: expected " + doc.getVersion() + ", got " + req.getVersion());
            }
        }

        // 상태 검증
        String newStatusStr = req.getStatus();
        RagDocumentStatus newStatus = RagDocumentStatus.fromString(newStatusStr);
        if (newStatus == null || 
            (newStatus != RagDocumentStatus.PROCESSING && 
             newStatus != RagDocumentStatus.COMPLETED && 
             newStatus != RagDocumentStatus.FAILED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "Invalid status: " + newStatusStr + ". Allowed: PROCESSING, COMPLETED, FAILED");
        }

        // 상태 업데이트
        doc.setStatus(newStatus);

        // processedAt 업데이트
        if (req.getProcessedAt() != null && !req.getProcessedAt().isBlank()) {
            try {
                Instant processedAt = Instant.parse(req.getProcessedAt());
                doc.setProcessedAt(processedAt);
            } catch (Exception e) {
                log.warn("Invalid processedAt format: {}, using current time", req.getProcessedAt());
                doc.setProcessedAt(Instant.now());
            }
        } else if (newStatus == RagDocumentStatus.COMPLETED || newStatus == RagDocumentStatus.FAILED) {
            // 상태가 COMPLETED나 FAILED인데 processedAt이 없으면 현재 시간 사용
            doc.setProcessedAt(Instant.now());
        }

        // failReason 로깅 및 전처리 상태 업데이트
        if (req.getFailReason() != null && !req.getFailReason().isBlank()) {
            log.warn("Document processing failed: id={}, documentId={}, version={}, failReason={}", 
                doc.getId(), doc.getDocumentId(), doc.getVersion(), req.getFailReason());
            // 실패 시 전처리 상태도 FAILED로 설정
            doc.setPreprocessStatus("FAILED");
            doc.setPreprocessError(req.getFailReason());
        } else if (newStatus == RagDocumentStatus.COMPLETED) {
            // 성공 시 전처리 상태를 READY로 설정하고 미리보기 데이터 저장
            doc.setPreprocessStatus("READY");
            if (req.getPreprocessPages() != null) {
                doc.setPreprocessPages(req.getPreprocessPages());
            }
            if (req.getPreprocessChars() != null) {
                doc.setPreprocessChars(req.getPreprocessChars());
            }
            if (req.getPreprocessExcerpt() != null && !req.getPreprocessExcerpt().isBlank()) {
                doc.setPreprocessExcerpt(req.getPreprocessExcerpt());
            }
        } else if (newStatus == RagDocumentStatus.PROCESSING) {
            // 처리 중일 때는 전처리 상태도 PROCESSING으로 설정
            doc.setPreprocessStatus("PROCESSING");
        }

        doc = documentRepository.save(doc);

        log.info("Document status updated: id={}, documentId={}, version={}, status={}, processedAt={}, failReason={}", 
            doc.getId(), doc.getDocumentId(), doc.getVersion(), doc.getStatus(), doc.getProcessedAt(), 
            req.getFailReason() != null ? req.getFailReason() : "N/A");

        return new InternalUpdateStatusResponse(
            doc.getId().toString(),
            doc.getDocumentId(),
            doc.getVersion(),
            doc.getStatus() != null ? doc.getStatus().name() : null,
            doc.getProcessedAt() != null ? doc.getProcessedAt().toString() : null,
            Instant.now().toString()
        );
    }

    // ========== Preprocess API Methods ==========

    /**
     * 전처리 미리보기 조회
     */
    public PreprocessPreviewResponse getPreprocessPreview(String documentId, Integer version) {
        RagDocument doc = documentRepository.findByDocumentIdAndVersion(documentId, version)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                "Policy version not found: " + documentId + " v" + version));
        
        return new PreprocessPreviewResponse(
            doc.getPreprocessStatus() != null ? doc.getPreprocessStatus() : "IDLE",
            doc.getPreprocessPages(),
            doc.getPreprocessChars(),
            doc.getPreprocessExcerpt(),
            doc.getPreprocessError()
        );
    }

    /**
     * 전처리 재시도
     */
    public RetryPreprocessResponse retryPreprocess(String documentId, Integer version) {
        RagDocument doc = documentRepository.findByDocumentIdAndVersion(documentId, version)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                "Policy version not found: " + documentId + " v" + version));
        
        // 전처리 상태를 PROCESSING으로 변경
        doc.setPreprocessStatus("PROCESSING");
        doc.setPreprocessError(null);
        documentRepository.save(doc);
        
        // 히스토리 기록
        addHistory(doc.getDocumentId(), doc.getVersion(), "PREPROCESS_RETRY", null, "전처리 재시도");
        
        // TODO: 실제 전처리 작업을 AI 서비스에 요청하는 로직 추가 필요
        // ragAiClient.requestPreprocess(doc.getId().toString());
        
        return new RetryPreprocessResponse(
            doc.getDocumentId(),
            doc.getVersion(),
            "PROCESSING",
            "전처리를 재시도합니다."
        );
    }

    // ========== History API Methods ==========

    /**
     * 히스토리 조회
     */
    public HistoryResponse getHistory(String documentId, Integer version) {
        // 문서 존재 확인
        RagDocument doc = documentRepository.findByDocumentIdAndVersion(documentId, version)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                "Policy version not found: " + documentId + " v" + version));
        
        List<RagDocumentHistory> histories = historyRepository
            .findByDocumentIdAndVersionOrderByCreatedAtDesc(documentId, version);
        
        List<HistoryItem> items = histories.stream()
            .map(h -> new HistoryItem(
                h.getId().toString(),
                h.getDocumentId(),
                h.getVersion(),
                h.getAction(),
                h.getActor(),
                h.getMessage(),
                h.getCreatedAt() != null ? h.getCreatedAt().toString() : null
            ))
            .collect(java.util.stream.Collectors.toList());
        
        return new HistoryResponse(documentId, version, items);
    }

    /**
     * 히스토리 기록 헬퍼 메서드
     */
    private void addHistory(String documentId, Integer version, String action, String actor, String message) {
        RagDocumentHistory history = new RagDocumentHistory();
        history.setDocumentId(documentId);
        history.setVersion(version);
        history.setAction(action);
        history.setActor(actor);
        history.setMessage(message);
        history.setCreatedAt(Instant.now());
        historyRepository.save(history);
    }

}

