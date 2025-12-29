package com.ctrlf.infra.rag.service;

import static com.ctrlf.infra.rag.dto.RagDtos.*;

import com.ctrlf.infra.rag.entity.RagDocument;
import com.ctrlf.infra.rag.entity.RagDocumentChunk;
import com.ctrlf.infra.rag.entity.RagFailChunk;
import com.ctrlf.infra.rag.client.RagAiClient;
import com.ctrlf.infra.rag.repository.RagDocumentChunkRepository;
import com.ctrlf.infra.rag.repository.RagFailChunkRepository;
import com.ctrlf.infra.rag.repository.RagDocumentRepository;
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
        d.setStatus("QUEUED");
        d.setCreatedAt(Instant.now());
        d = documentRepository.save(d);

        return new UploadResponse(
            d.getId().toString(),
            d.getStatus(),
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
        try {
            ragAiClient.process(d.getId(), d.getTitle(), d.getDomain(), d.getSourceUrl(), Instant.now());
        } catch (Exception e) {
            // 로그만
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
        boolean accepted = true;
        String jobId = "unknown";
        String status = "REPROCESSING";
        try {
            RagAiClient.AiResponse aiResp =
                ragAiClient.process(d.getId(), d.getTitle(), d.getDomain(), d.getSourceUrl(), Instant.now());
            accepted = aiResp.isAccepted();
            jobId = aiResp.getJobId();
            status = aiResp.getStatus() == null ? status : aiResp.getStatus();
        } catch (Exception e) {
            accepted = false;
        }
        return new ReprocessResponse(id.toString(), accepted, status, jobId, Instant.now().toString());
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
            d.getStatus() != null ? d.getStatus() : "QUEUED"
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
            d.getStatus() != null ? d.getStatus() : "QUEUED",
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
        UUID docId = parseUuid(documentId);
        
        // 문서 존재 확인
        if (!documentRepository.existsById(docId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "document not found: " + documentId);
        }

        // 기존 청크 삭제 (재적재 시)
        chunkRepository.deleteByDocumentId(docId);

        // 새 청크 저장
        List<RagDocumentChunk> chunks = new ArrayList<>();
        for (ChunkItem item : req.getChunks()) {
            RagDocumentChunk chunk = new RagDocumentChunk();
            chunk.setDocumentId(docId);
            chunk.setChunkIndex(item.getChunkIndex());
            chunk.setChunkText(item.getChunkText());
            chunk.setCreatedAt(Instant.now());
            // embedding은 null (Milvus에 저장됨)
            // chunkMeta는 현재 DB 스키마에 없으므로 저장하지 않음 (추후 추가 가능)
            chunks.add(chunk);
        }
        
        chunkRepository.saveAll(chunks);
        
        log.info("청크 bulk upsert 완료: documentId={}, count={}", documentId, chunks.size());
        
        return new ChunksBulkUpsertResponse(true, chunks.size());
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

    // ========== Policy Management Methods ==========

    /**
     * 사규 목록 조회 (document_id별 그룹화)
     */
    public List<PolicyListItem> listPolicies(String search, String status, boolean includeArchived, boolean includeDeleted) {
        List<RagDocument> documents;
        
        if (includeArchived || includeDeleted) {
            // 보관/삭제 포함 옵션은 상태가 "전체"일 때만 적용
            String statusFilter = "전체".equals(status) ? null : status;
            documents = documentRepository.findPolicies(search, statusFilter);
        } else {
            // 기본적으로 ARCHIVED 제외
            String statusFilter = "전체".equals(status) ? null : status;
            documents = documentRepository.findPoliciesExcludingArchived(search, statusFilter);
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
                    v.getStatus(),
                    v.getCreatedAt() != null ? v.getCreatedAt().toString() : null
                ))
                .collect(java.util.stream.Collectors.toList());
            
            result.add(new PolicyListItem(
                docId,
                first.getTitle(),
                first.getDomain(),
                versionSummaries,
                versions.size()
            ));
        }
        
        return result;
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
            .map(v -> new VersionDetail(
                v.getId().toString(),
                v.getDocumentId(),
                v.getTitle(),
                v.getDomain(),
                v.getVersion(),
                v.getStatus(),
                v.getChangeSummary(),
                v.getSourceUrl(),
                v.getUploaderUuid(),
                v.getCreatedAt() != null ? v.getCreatedAt().toString() : null,
                v.getProcessedAt() != null ? v.getProcessedAt().toString() : null
            ))
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
        
        return new VersionDetail(
            doc.getId().toString(),
            doc.getDocumentId(),
            doc.getTitle(),
            doc.getDomain(),
            doc.getVersion(),
            doc.getStatus(),
            doc.getChangeSummary(),
            doc.getSourceUrl(),
            doc.getUploaderUuid(),
            doc.getCreatedAt() != null ? doc.getCreatedAt().toString() : null,
            doc.getProcessedAt() != null ? doc.getProcessedAt().toString() : null
        );
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
            .map(v -> new VersionDetail(
                v.getId().toString(),
                v.getDocumentId(),
                v.getTitle(),
                v.getDomain(),
                v.getVersion(),
                v.getStatus(),
                v.getChangeSummary(),
                v.getSourceUrl(),
                v.getUploaderUuid(),
                v.getCreatedAt() != null ? v.getCreatedAt().toString() : null,
                v.getProcessedAt() != null ? v.getProcessedAt().toString() : null
            ))
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
        doc.setStatus("DRAFT");
        doc.setChangeSummary(req.getChangeSummary());
        doc.setSourceUrl(req.getFileUrl());
        doc.setUploaderUuid(uploaderUuid.toString());
        doc.setCreatedAt(Instant.now());
        
        doc = documentRepository.save(doc);
        
        return new CreatePolicyResponse(
            doc.getId().toString(),
            doc.getDocumentId(),
            doc.getTitle(),
            doc.getVersion(),
            doc.getStatus(),
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
        newVersionDoc.setStatus("DRAFT");
        newVersionDoc.setChangeSummary(req.getChangeSummary());
        newVersionDoc.setSourceUrl(req.getFileUrl());
        newVersionDoc.setUploaderUuid(uploaderUuid.toString());
        newVersionDoc.setCreatedAt(Instant.now());
        
        newVersionDoc = documentRepository.save(newVersionDoc);
        
        return new CreateVersionResponse(
            newVersionDoc.getId().toString(),
            newVersionDoc.getDocumentId(),
            newVersionDoc.getVersion(),
            newVersionDoc.getStatus(),
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
            doc.getStatus(),
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
        
        String newStatus = req.getStatus();
        if (!java.util.List.of("ACTIVE", "DRAFT", "PENDING", "ARCHIVED").contains(newStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "Invalid status: " + newStatus);
        }
        
        // ACTIVE로 변경 시, 같은 document_id의 다른 ACTIVE 버전들을 DRAFT로 변경
        if ("ACTIVE".equals(newStatus)) {
            List<RagDocument> activeVersions = documentRepository.findByDocumentId(documentId).stream()
                .filter(v -> "ACTIVE".equals(v.getStatus()) && !v.getVersion().equals(version))
                .collect(java.util.stream.Collectors.toList());
            
            for (RagDocument active : activeVersions) {
                active.setStatus("DRAFT");
                documentRepository.save(active);
            }
        }
        
        doc.setStatus(newStatus);
        doc = documentRepository.save(doc);
        
        return new UpdateStatusResponse(
            doc.getId().toString(),
            doc.getDocumentId(),
            doc.getVersion(),
            doc.getStatus(),
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
        doc = documentRepository.save(doc);
        
        return new ReplaceFileResponse(
            doc.getId().toString(),
            doc.getDocumentId(),
            doc.getVersion(),
            doc.getSourceUrl(),
            Instant.now().toString()
        );
    }
}

