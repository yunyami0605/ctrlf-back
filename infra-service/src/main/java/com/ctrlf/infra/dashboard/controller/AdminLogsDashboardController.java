package com.ctrlf.infra.dashboard.controller;

import com.ctrlf.infra.ailog.dto.AiLogDtos;
import com.ctrlf.infra.ailog.service.AiLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 대시보드 - 로그 탭 API 컨트롤러
 * 
 * <p>AI 로그 데이터 기반 관리자 대시보드 로그 조회를 제공합니다.</p>
 */
@Tag(name = "Admin-Dashboard-Logs", description = "관리자 대시보드 로그 탭 API (ADMIN)")
@RestController
@RequestMapping("/admin/dashboard/logs")
@SecurityRequirement(name = "bearer-jwt")
@RequiredArgsConstructor
public class AdminLogsDashboardController {

    private final AiLogService aiLogService;

    /**
     * 세부 로그 목록 조회
     */
    @GetMapping
    @Operation(
        summary = "세부 로그 목록 조회",
        description = "필터링 및 페이징을 지원하는 AI 로그 목록을 조회합니다."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "성공",
            content = @Content(schema = @Schema(implementation = AiLogDtos.PageResponse.class))
        ),
        @ApiResponse(responseCode = "401", description = "인증 실패"),
        @ApiResponse(responseCode = "403", description = "권한 없음"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<AiLogDtos.PageResponse<AiLogDtos.LogListItem>> getLogs(
        @Parameter(description = "기간 (7 | 30 | 90)", example = "30")
        @RequestParam(value = "period", required = false) String period,
        @Parameter(description = "시작 날짜 (ISO 8601)", example = "2025-12-06T15:00:00.000Z")
        @RequestParam(value = "startDate", required = false) String startDate,
        @Parameter(description = "종료 날짜 (ISO 8601)", example = "2026-01-06T14:59:59.999Z")
        @RequestParam(value = "endDate", required = false) String endDate,
        @Parameter(description = "부서명", example = "총무팀")
        @RequestParam(value = "department", required = false) String department,
        @Parameter(description = "도메인 ID", example = "SECURITY")
        @RequestParam(value = "domain", required = false) String domain,
        @Parameter(description = "라우트 ID", example = "RAG")
        @RequestParam(value = "route", required = false) String route,
        @Parameter(description = "모델 ID", example = "gpt-4o-mini")
        @RequestParam(value = "model", required = false) String model,
        @Parameter(description = "에러만 보기", example = "false")
        @RequestParam(value = "onlyError", required = false) Boolean onlyError,
        @Parameter(description = "PII 포함만 보기", example = "false")
        @RequestParam(value = "hasPiiOnly", required = false) Boolean hasPiiOnly,
        @Parameter(description = "페이지 번호 (기본값: 0)", example = "0")
        @RequestParam(value = "page", required = false) Integer page,
        @Parameter(description = "페이지 크기 (기본값: 20)", example = "20")
        @RequestParam(value = "size", required = false) Integer size,
        @Parameter(description = "정렬 (예: createdAt,desc)", example = "createdAt,desc")
        @RequestParam(value = "sort", required = false) String sort
    ) {
        return ResponseEntity.ok(aiLogService.getLogs(
            period,
            startDate,
            endDate,
            department,
            domain,
            route,
            model,
            onlyError,
            hasPiiOnly,
            page,
            size,
            sort
        ));
    }
}

