package com.ctrlf.infra.rag.service;

import static com.ctrlf.infra.rag.dto.RagDtos.*;

import com.ctrlf.infra.rag.entity.RagDocument;
import com.ctrlf.infra.rag.ai.RagAiClient;
import com.ctrlf.infra.rag.repository.RagDocumentChunkRepository;
import com.ctrlf.infra.rag.repository.RagFailChunkRepository;
import com.ctrlf.infra.rag.repository.RagDocumentRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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

    private final RagDocumentRepository documentRepository;
    private final RagDocumentChunkRepository chunkRepository;
    private final RagFailChunkRepository failChunkRepository;
    private final RagAiClient ragAiClient;

    /**
     * 문서 업로드 메타를 저장하고 초기 상태를 반환합니다.
     * @param req 업로드 요청 DTO(제목/도메인/업로더UUID/파일URL)
     * @return 업로드 응답(문서ID, 상태=QUEUED, 생성시각)
     */
    public UploadResponse upload(UploadRequest req) {
        RagDocument d = new RagDocument();
        d.setTitle(req.getTitle());
        d.setDomain(req.getDomain());
        d.setUploaderUuid(req.getUploaderUuid());
        d.setSourceUrl(req.getFileUrl());
        d.setCreatedAt(Instant.now());
        d = documentRepository.save(d);

        // 업로드 직후 AI 서버에 처리 요청 (베스트Effort)
        try {
            ragAiClient.process(d.getId(), d.getTitle(), d.getDomain(), d.getSourceUrl(), Instant.now());
        } catch (Exception e) {
            // 실패해도 업로드 API는 성공으로 처리. 로그만 남김
            // 운영에서는 재시도 큐에 적재하는 것을 권장
        }

        return new UploadResponse(
            d.getId().toString(),
            "QUEUED",
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

    private static UUID parseUuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid uuid");
        }
    }
}

