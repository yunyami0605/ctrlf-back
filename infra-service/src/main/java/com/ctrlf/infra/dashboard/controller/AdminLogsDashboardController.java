package com.ctrlf.infra.dashboard.controller;

import com.ctrlf.infra.ailog.dto.AiLogDtos;
import com.ctrlf.infra.ailog.service.AiLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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
        @ApiResponse(responseCode = "400", description = "잘못된 요청 (period 값이 7, 30, 90 중 하나가 아님)"),
        @ApiResponse(responseCode = "401", description = "인증 실패"),
        @ApiResponse(responseCode = "403", description = "권한 없음"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<AiLogDtos.PageResponse<AiLogDtos.LogListItem>> getLogs(
        @Valid @ModelAttribute AiLogDtos.LogListRequest request
    ) {
        // 기본값 적용
        request.applyDefaults();

        // period 검증
        if (!request.isValidPeriod()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "period는 7, 30, 90 중 하나여야 합니다."
            );
        }

        try {
            return ResponseEntity.ok(aiLogService.getLogs(request));
        } catch (Exception e) {
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "로그 조회 중 오류가 발생했습니다: " + e.getMessage(),
                e
            );
        }
    }
}

