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
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@Tag(name = "RAG - Documents", description = "RAG 문서 관리 API (임시)")
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
                examples = @ExampleObject(name = "request", value = "{\n  \"title\": \"산업안전 규정집 v3\",\n  \"domain\": \"HR\",\n  \"uploaderUuid\": \"c13c91f2-fb1a-4d42-b381-72847a52fb99\",\n  \"fileUrl\": \"s3://bucket/docs/hr_safety_v3.pdf\"\n}")
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
     */
    public ResponseEntity<UploadResponse> upload(@Valid @RequestBody UploadRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ragDocumentService.upload(req));
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
}

