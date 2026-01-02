package com.ctrlf.infra.rag.controller;

import static com.ctrlf.infra.rag.dto.RagDtos.*;

import com.ctrlf.infra.rag.service.RagDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/rag/documents")
@RequiredArgsConstructor
@Tag(name = "Internal RAG - Documents", description = "RAG 문서 내부 API (AI 서버 → Backend)")
/**
 * RAG 문서 내부 API 컨트롤러.
 * AI 서버에서 호출하는 내부 API만 포함합니다.
 */
public class InternalRagDocumentController {

    private static final Logger log = LoggerFactory.getLogger(InternalRagDocumentController.class);
    private final RagDocumentService ragDocumentService;

    @PatchMapping("/{ragDocumentPk}/status")
    @Operation(
        summary = "사규 상태 업데이트 (AI -> Backend 내부 API)",
        description = "AI 서버가 사규 문서의 임베딩 처리 상태를 업데이트합니다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "상태 업데이트 성공",
            content = @Content(schema = @Schema(implementation = InternalUpdateStatusResponse.class))),
        @ApiResponse(responseCode = "400", description = "잘못된 요청"),
        @ApiResponse(responseCode = "401", description = "내부 토큰 오류"),
        @ApiResponse(responseCode = "404", description = "문서를 찾을 수 없음")
    })
    public ResponseEntity<InternalUpdateStatusResponse> updateDocumentStatus(
        @Parameter(description = "RAG 문서 ID (UUID)", required = true) 
        @PathVariable("ragDocumentPk") UUID ragDocumentPk,
        @RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
        @Valid @RequestBody InternalUpdateStatusRequest req
    ) {
        return ResponseEntity.ok(ragDocumentService.updateDocumentStatus(ragDocumentPk, req));
    }

    @PostMapping("/{documentId}/chunks:bulk")
    @Operation(
        summary = "문서 청크 Bulk Upsert (내부 API)",
        description = "FastAPI가 문서 청크를 bulk upsert합니다. (임베딩 벡터는 Milvus에 저장, DB는 chunk_text만 저장)"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "저장 성공",
            content = @Content(schema = @Schema(implementation = ChunksBulkUpsertResponse.class))),
        @ApiResponse(responseCode = "400", description = "잘못된 요청"),
        @ApiResponse(responseCode = "401", description = "내부 토큰 오류"),
        @ApiResponse(responseCode = "404", description = "문서를 찾을 수 없음")
    })
    public ResponseEntity<ChunksBulkUpsertResponse> bulkUpsertChunks(
        @Parameter(description = "문서 ID", required = true) @PathVariable("documentId") String documentId,
        @RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
        @Valid @RequestBody ChunksBulkUpsertRequest req
    ) {
        log.info("청크 bulk upsert 요청 수신: documentId={}, chunksCount={}", 
            documentId, req.getChunks() != null ? req.getChunks().size() : 0);
        ChunksBulkUpsertResponse response = ragDocumentService.bulkUpsertChunks(documentId, req);
        log.info("청크 bulk upsert 완료: documentId={}, savedCount={}", documentId, response.getSavedCount());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{documentId}/fail-chunks:bulk")
    @Operation(
        summary = "임베딩 실패 로그 Bulk Upsert (내부 API)",
        description = "FastAPI가 임베딩 실패한 청크 로그를 bulk upsert합니다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "저장 성공",
            content = @Content(schema = @Schema(implementation = FailChunksBulkUpsertResponse.class))),
        @ApiResponse(responseCode = "400", description = "잘못된 요청"),
        @ApiResponse(responseCode = "401", description = "내부 토큰 오류"),
        @ApiResponse(responseCode = "404", description = "문서를 찾을 수 없음")
    })
    public ResponseEntity<FailChunksBulkUpsertResponse> bulkUpsertFailChunks(
        @Parameter(description = "문서 ID", required = true) @PathVariable("documentId") String documentId,
        @RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
        @Valid @RequestBody FailChunksBulkUpsertRequest req
    ) {
        return ResponseEntity.ok(ragDocumentService.bulkUpsertFailChunks(documentId, req));
    }
}

