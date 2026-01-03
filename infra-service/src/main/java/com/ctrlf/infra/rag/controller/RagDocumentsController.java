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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import com.ctrlf.common.dto.PageResponse;
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
        summary = "RAG 문서 업로드 메타 등록 (Frontend -> Backend API)",
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

    // ========== Policy Management APIs ==========

    @GetMapping("/policies")
    @Operation(
        summary = "사규 목록 조회",
        description = "사규 목록을 document_id별로 그룹화하여 조회합니다. 검색, 상태 필터, 페이지네이션을 지원합니다. 기본적으로 ARCHIVED 상태는 제외됩니다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "사규 목록 조회 성공",
            content = @Content(schema = @Schema(implementation = PageResponse.class))),
        @ApiResponse(responseCode = "400", description = "잘못된 필터 값")
    })
    public ResponseEntity<PageResponse<PolicyListItem>> listPolicies(
        @Parameter(description = "document_id 또는 제목 검색어") @RequestParam(value = "search", required = false) String search,
        @Parameter(description = "상태 필터 (ACTIVE, DRAFT, PENDING, ARCHIVED, 전체). ARCHIVED를 조회하려면 status=ARCHIVED로 명시적으로 지정하세요.") @RequestParam(value = "status", required = false) String status,
        @Parameter(description = "페이지네이션 (page, size)") @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(ragDocumentService.listPolicies(search, status, pageable));
    }

    @GetMapping("/policies/{documentId}")
    @Operation(
        summary = "사규 상세 조회",
        description = "document_id로 사규의 모든 버전을 조회합니다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "사규 상세 조회 성공",
            content = @Content(schema = @Schema(implementation = PolicyDetailResponse.class))),
        @ApiResponse(responseCode = "404", description = "사규를 찾을 수 없음")
    })
    public ResponseEntity<PolicyDetailResponse> getPolicy(
        @Parameter(description = "사규 document_id", example = "POL-EDU-015") @PathVariable("documentId") String documentId
    ) {
        return ResponseEntity.ok(ragDocumentService.getPolicy(documentId));
    }

    @GetMapping("/policies/{documentId}/versions/{version}")
    @Operation(
        summary = "버전별 상세 조회",
        description = "특정 버전의 상세 정보를 조회합니다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "버전 상세 조회 성공",
            content = @Content(schema = @Schema(implementation = VersionDetail.class))),
        @ApiResponse(responseCode = "404", description = "버전을 찾을 수 없음")
    })
    public ResponseEntity<VersionDetail> getPolicyVersion(
        @Parameter(description = "사규 document_id", example = "POL-EDU-015") @PathVariable("documentId") String documentId,
        @Parameter(description = "버전 번호", example = "1") @PathVariable("version") Integer version
    ) {
        return ResponseEntity.ok(ragDocumentService.getPolicyVersion(documentId, version));
    }

    @GetMapping("/policies/{documentId}/versions")
    @Operation(
        summary = "버전 목록 조회",
        description = "사규의 모든 버전 목록을 조회합니다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "버전 목록 조회 성공",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = VersionDetail.class)))),
        @ApiResponse(responseCode = "404", description = "사규를 찾을 수 없음")
    })
    public ResponseEntity<List<VersionDetail>> getPolicyVersions(
        @Parameter(description = "사규 document_id", example = "POL-EDU-015") @PathVariable("documentId") String documentId
    ) {
        return ResponseEntity.ok(ragDocumentService.getPolicyVersions(documentId));
    }

    @PostMapping("/policies")
    @Operation(
        summary = "새 사규 생성",
        description = "새로운 사규를 생성합니다. 초기 버전(v1)이 DRAFT 상태로 생성됩니다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "사규 생성 성공",
            content = @Content(schema = @Schema(implementation = CreatePolicyResponse.class))),
        @ApiResponse(responseCode = "400", description = "잘못된 요청"),
        @ApiResponse(responseCode = "409", description = "이미 존재하는 document_id")
    })
    public ResponseEntity<CreatePolicyResponse> createPolicy(
        @Valid @RequestBody CreatePolicyRequest req,
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
            .body(ragDocumentService.createPolicy(req, uploaderUuid));
    }

    @PostMapping("/policies/{documentId}/versions")
    @Operation(
        summary = "새 버전 생성",
        description = "기존 사규의 새 버전을 생성합니다. 버전 번호는 자동으로 증가하며 DRAFT 상태로 생성됩니다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "버전 생성 성공",
            content = @Content(schema = @Schema(implementation = CreateVersionResponse.class))),
        @ApiResponse(responseCode = "404", description = "사규를 찾을 수 없음")
    })
    public ResponseEntity<CreateVersionResponse> createVersion(
        @Parameter(description = "사규 document_id", example = "POL-EDU-015") @PathVariable("documentId") String documentId,
        @Valid @RequestBody CreateVersionRequest req,
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
            .body(ragDocumentService.createVersion(documentId, req, uploaderUuid));
    }

    @PatchMapping("/policies/{documentId}/versions/{version}")
    @Operation(
        summary = "버전 수정",
        description = "사규 버전의 title, change_summary, 파일을 수정합니다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "버전 수정 성공",
            content = @Content(schema = @Schema(implementation = UpdateVersionResponse.class))),
        @ApiResponse(responseCode = "404", description = "버전을 찾을 수 없음")
    })
    public ResponseEntity<UpdateVersionResponse> updateVersion(
        @Parameter(description = "사규 document_id", example = "POL-EDU-015") @PathVariable("documentId") String documentId,
        @Parameter(description = "버전 번호", example = "2") @PathVariable("version") Integer version,
        @Valid @RequestBody UpdateVersionRequest req
    ) {
        return ResponseEntity.ok(ragDocumentService.updateVersion(documentId, version, req));
    }

    @PatchMapping("/policies/{documentId}/versions/{version}/status")
    @Operation(
        summary = "상태 변경",
        description = "사규 버전의 상태를 변경합니다. ACTIVE로 변경 시 같은 document_id의 다른 ACTIVE 버전은 자동으로 DRAFT로 변경됩니다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "상태 변경 성공",
            content = @Content(schema = @Schema(implementation = UpdateStatusResponse.class))),
        @ApiResponse(responseCode = "400", description = "잘못된 상태 값"),
        @ApiResponse(responseCode = "404", description = "버전을 찾을 수 없음")
    })
    public ResponseEntity<UpdateStatusResponse> updateStatus(
        @Parameter(description = "사규 document_id", example = "POL-EDU-015") @PathVariable("documentId") String documentId,
        @Parameter(description = "버전 번호", example = "2") @PathVariable("version") Integer version,
        @Valid @RequestBody UpdateStatusRequest req
    ) {
        return ResponseEntity.ok(ragDocumentService.updateStatus(documentId, version, req));
    }

    @PostMapping("/policies/{documentId}/versions/{version}/review/approve")
    @Operation(
        summary = "검토 승인",
        description = "검토 대기(PENDING) 상태인 사규를 승인하여 ACTIVE 상태로 변경합니다. 같은 document_id의 다른 ACTIVE 버전은 자동으로 ARCHIVED로 변경됩니다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "승인 성공",
            content = @Content(schema = @Schema(implementation = ReviewResponse.class))),
        @ApiResponse(responseCode = "400", description = "PENDING 상태가 아님"),
        @ApiResponse(responseCode = "404", description = "버전을 찾을 수 없음")
    })
    public ResponseEntity<ReviewResponse> approveReview(
        @Parameter(description = "사규 document_id", example = "POL-EDU-015") @PathVariable("documentId") String documentId,
        @Parameter(description = "버전 번호", example = "2") @PathVariable("version") Integer version,
        @Valid @RequestBody ApproveReviewRequest req
    ) {
        return ResponseEntity.ok(ragDocumentService.approveReview(documentId, version, req));
    }

    @PostMapping("/policies/{documentId}/versions/{version}/review/reject")
    @Operation(
        summary = "검토 반려",
        description = "검토 대기(PENDING) 상태인 사규를 반려하여 REJECTED 상태로 변경합니다. 반려 사유를 필수로 입력해야 합니다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "반려 성공",
            content = @Content(schema = @Schema(implementation = ReviewResponse.class))),
        @ApiResponse(responseCode = "400", description = "PENDING 상태가 아님 또는 반려 사유 누락"),
        @ApiResponse(responseCode = "404", description = "버전을 찾을 수 없음")
    })
    public ResponseEntity<ReviewResponse> rejectReview(
        @Parameter(description = "사규 document_id", example = "POL-EDU-015") @PathVariable("documentId") String documentId,
        @Parameter(description = "버전 번호", example = "2") @PathVariable("version") Integer version,
        @Valid @RequestBody RejectReviewRequest req
    ) {
        return ResponseEntity.ok(ragDocumentService.rejectReview(documentId, version, req));
    }

    @PutMapping("/policies/{documentId}/versions/{version}/file")
    @Operation(
        summary = "파일 업로드/교체",
        description = "사규 버전의 파일을 교체합니다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "파일 교체 성공",
            content = @Content(schema = @Schema(implementation = ReplaceFileResponse.class))),
        @ApiResponse(responseCode = "404", description = "버전을 찾을 수 없음")
    })
    public ResponseEntity<ReplaceFileResponse> replaceFile(
        @Parameter(description = "사규 document_id", example = "POL-EDU-015") @PathVariable("documentId") String documentId,
        @Parameter(description = "버전 번호", example = "2") @PathVariable("version") Integer version,
        @Valid @RequestBody ReplaceFileRequest req
    ) {
        return ResponseEntity.ok(ragDocumentService.replaceFile(documentId, version, req));
    }

    @GetMapping("/policies/{documentId}/versions/{version}/preprocess")
    @Operation(
        summary = "전처리 미리보기 조회",
        description = "사규 버전의 전처리 상태와 미리보기를 조회합니다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "전처리 미리보기 조회 성공",
            content = @Content(schema = @Schema(implementation = PreprocessPreviewResponse.class))),
        @ApiResponse(responseCode = "404", description = "버전을 찾을 수 없음")
    })
    public ResponseEntity<PreprocessPreviewResponse> getPreprocessPreview(
        @Parameter(description = "사규 document_id", example = "POL-EDU-015") @PathVariable("documentId") String documentId,
        @Parameter(description = "버전 번호", example = "1") @PathVariable("version") Integer version
    ) {
        return ResponseEntity.ok(ragDocumentService.getPreprocessPreview(documentId, version));
    }

    @PostMapping("/policies/{documentId}/versions/{version}/preprocess/retry")
    @Operation(
        summary = "전처리 재시도",
        description = "사규 버전의 전처리를 재시도합니다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "전처리 재시도 성공",
            content = @Content(schema = @Schema(implementation = RetryPreprocessResponse.class))),
        @ApiResponse(responseCode = "404", description = "버전을 찾을 수 없음")
    })
    public ResponseEntity<RetryPreprocessResponse> retryPreprocess(
        @Parameter(description = "사규 document_id", example = "POL-EDU-015") @PathVariable("documentId") String documentId,
        @Parameter(description = "버전 번호", example = "1") @PathVariable("version") Integer version,
        @Valid @RequestBody RetryPreprocessRequest req
    ) {
        return ResponseEntity.ok(ragDocumentService.retryPreprocess(documentId, version));
    }

    @GetMapping("/policies/{documentId}/versions/{version}/history")
    @Operation(
        summary = "히스토리 조회",
        description = "사규 버전의 히스토리를 조회합니다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "히스토리 조회 성공",
            content = @Content(schema = @Schema(implementation = HistoryResponse.class))),
        @ApiResponse(responseCode = "404", description = "버전을 찾을 수 없음")
    })
    public ResponseEntity<HistoryResponse> getHistory(
        @Parameter(description = "사규 document_id", example = "POL-EDU-015") @PathVariable("documentId") String documentId,
        @Parameter(description = "버전 번호", example = "1") @PathVariable("version") Integer version
    ) {
        return ResponseEntity.ok(ragDocumentService.getHistory(documentId, version));
    }

}

