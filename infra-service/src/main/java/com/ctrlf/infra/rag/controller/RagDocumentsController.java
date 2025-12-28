package com.ctrlf.infra.rag.controller;

import static com.ctrlf.infra.rag.dto.RagDtos.*;

import com.ctrlf.infra.rag.service.RagDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import com.ctrlf.common.security.SecurityUtils;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rag/documents")
@RequiredArgsConstructor
@Tag(name = "RAG - Documents", description = "RAG 문서 관리 API")
/**
 * RAG 문서 관리용 REST 컨트롤러.
 * - 업로드 메타 등록(POST /rag/documents/upload)
 * - 문서 목록 조회(GET /rag/documents)
 *
 * 인프라 서비스에 임시로 위치시키되, 나중에 rag-service로의 분리를 염두에 두고
 * 서비스/DTO/리포지토리 의존을 분명히 분리합니다.
 */
public class RagDocumentsController {

    private final RagDocumentService ragDocumentService;

    @PostMapping("/upload")
    @Operation(
        summary = "RAG 문서 업로드 메타 등록",
        description = "S3에 업로드 완료된 문서의 메타 정보를 저장하고, 초기 상태(QUEUED)를 반환합니다.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = UploadRequest.class),
                examples = @ExampleObject(name = "request", value = "{\n  \"title\": \"산업안전 규정집 v3\",\n  \"domain\": \"HR\",\n  \"fileUrl\": \"s3://ctrl-s3/docs/hr_safety_v3.pdf\"\n}")
            )
        )
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "문서 등록 성공",
            content = @Content(schema = @Schema(implementation = UploadResponse.class))),
        @ApiResponse(responseCode = "400", description = "잘못된 요청"),
        @ApiResponse(responseCode = "401", description = "인증 실패"),
        @ApiResponse(responseCode = "403", description = "권한 없음"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    /**
     * 문서 업로드 메타 정보를 저장합니다.
     * 실제 파일 업로드는 S3 presigned URL을 통해 선행되며,
     * 여기서는 제목/도메인/업로더/파일URL을 DB에 기록하고 초기 상태(QUEUED)를 반환합니다.
     * uploaderUuid는 JWT 토큰에서 자동으로 추출됩니다.
     */
    public ResponseEntity<UploadResponse> upload(
        @Valid @RequestBody UploadRequest req,
        @AuthenticationPrincipal Jwt jwt
    ) {
        if (jwt == null) {
            throw new IllegalArgumentException("JWT 토큰이 없습니다. 인증이 필요합니다.");
        }
        
        UUID uploaderUuid = SecurityUtils.extractUserUuid(jwt)
            .orElseThrow(() -> {
                String sub = jwt.getSubject();
                return new IllegalArgumentException(
                    String.format("JWT 토큰에서 사용자 UUID를 추출할 수 없습니다. subject: %s", sub));
            });
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ragDocumentService.upload(req, uploaderUuid));
    }

    @GetMapping
    @Operation(
        summary = "RAG 문서 목록 조회",
        description = "등록된 문서 목록을 조회합니다. domain/uploaderUuid/date range/keyword 필터와 page/size 페이징을 지원합니다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "문서 목록 조회 성공",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = DocumentListItem.class)))),
        @ApiResponse(responseCode = "400", description = "잘못된 필터 값"),
        @ApiResponse(responseCode = "401", description = "인증 실패"),
        @ApiResponse(responseCode = "403", description = "권한 없음"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    /**
     * RAG 문서 목록을 필터/페이징으로 조회합니다.
     * - domain/uploaderUuid/date range/keyword를 지원합니다.
     */
    public ResponseEntity<List<DocumentListItem>> list(
        @Parameter(description = "문서 도메인") @RequestParam(value = "domain", required = false) String domain,
        @Parameter(description = "업로더 UUID") @RequestParam(value = "uploaderUuid", required = false) String uploaderUuid,
        @Parameter(description = "기간 시작(yyyy-MM-dd)") @RequestParam(value = "startDate", required = false) String startDate,
        @Parameter(description = "기간 끝(yyyy-MM-dd)") @RequestParam(value = "endDate", required = false) String endDate,
        @Parameter(description = "제목 키워드") @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(
            ragDocumentService.list(domain, uploaderUuid, startDate, endDate, keyword, page, size)
        );
    }

    @PatchMapping("/{id}")
    @Operation(summary = "RAG 문서 수정(Update)")
    public ResponseEntity<UpdateResponse> update(
        @PathVariable("id") String id,
        @Valid @RequestBody UpdateRequest req
    ) {
        return ResponseEntity.ok(ragDocumentService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "RAG 문서 삭제(Delete)")
    public ResponseEntity<DeleteResponse> delete(@PathVariable("id") String id) {
        return ResponseEntity.ok(ragDocumentService.delete(id));
    }

    @PostMapping("/{id}/reprocess")
    @Operation(summary = "RAG 문서 재처리 요청")
    public ResponseEntity<ReprocessResponse> reprocess(
        @PathVariable("id") String id,
        @Valid @RequestBody ReprocessRequest req
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ragDocumentService.reprocess(id, req));
    }

    @GetMapping("/{id}/status")
    @Operation(
        summary = "RAG 문서 처리 상태 조회",
        description = "문서의 임베딩 처리 상태를 조회합니다. (QUEUED, PROCESSING, COMPLETED, FAILED)"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "상태 조회 성공",
            content = @Content(schema = @Schema(implementation = DocumentStatusResponse.class))),
        @ApiResponse(responseCode = "404", description = "문서를 찾을 수 없음")
    })
    public ResponseEntity<DocumentStatusResponse> getStatus(
        @Parameter(description = "문서 ID") @PathVariable("id") String id
    ) {
        return ResponseEntity.ok(ragDocumentService.getStatus(id));
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "RAG 문서 정보 조회",
        description = "문서의 메타 정보를 조회합니다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "문서 정보 조회 성공",
            content = @Content(schema = @Schema(implementation = DocumentInfoResponse.class))),
        @ApiResponse(responseCode = "404", description = "문서를 찾을 수 없음")
    })
    public ResponseEntity<DocumentInfoResponse> getDocument(
        @Parameter(description = "문서 ID") @PathVariable("id") String id
    ) {
        return ResponseEntity.ok(ragDocumentService.getDocument(id));
    }

    @GetMapping("/{id}/text")
    @Operation(
        summary = "RAG 문서 원문 텍스트 조회",
        description = "문서의 원문 텍스트를 조회합니다. S3에서 파일을 다운로드하여 텍스트를 추출합니다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "텍스트 조회 성공",
            content = @Content(schema = @Schema(implementation = DocumentTextResponse.class))),
        @ApiResponse(responseCode = "404", description = "문서를 찾을 수 없음"),
        @ApiResponse(responseCode = "500", description = "텍스트 추출 실패")
    })
    public ResponseEntity<DocumentTextResponse> getText(
        @Parameter(description = "문서 ID") @PathVariable("id") String id
    ) {
        return ResponseEntity.ok(ragDocumentService.getText(id));
    }

    // ========================
    // 내부 API (FastAPI → Spring)
    // ========================

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
        @Valid @RequestBody ChunksBulkUpsertRequest req
    ) {
        return ResponseEntity.ok(ragDocumentService.bulkUpsertChunks(documentId, req));
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
        @Valid @RequestBody FailChunksBulkUpsertRequest req
    ) {
        return ResponseEntity.ok(ragDocumentService.bulkUpsertFailChunks(documentId, req));
    }
}

