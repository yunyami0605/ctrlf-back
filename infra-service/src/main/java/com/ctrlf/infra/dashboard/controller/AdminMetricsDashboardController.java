package com.ctrlf.infra.dashboard.controller;

import com.ctrlf.infra.telemetry.dto.TelemetryDtos;
import com.ctrlf.infra.telemetry.service.TelemetryService;
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
 * 관리자 대시보드 - 지표 탭 API 컨트롤러
 * 
 * <p>텔레메트리 데이터 기반 통합 지표를 제공합니다.</p>
 */
@Tag(name = "Admin-Dashboard-Metrics", description = "관리자 대시보드 지표 탭 API (ADMIN)")
@RestController
@RequestMapping("/admin/dashboard/metrics")
@SecurityRequirement(name = "bearer-jwt")
@RequiredArgsConstructor
public class AdminMetricsDashboardController {

    private final TelemetryService telemetryService;

    /**
     * 보안 지표 조회
     */
    @GetMapping("/security")
    @Operation(
        summary = "보안 지표 조회",
        description = "PII 차단 수, 외부 도메인 차단 수, PII 추이를 조회합니다."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "성공",
            content = @Content(schema = @Schema(implementation = TelemetryDtos.SecurityMetricsResponse.class))
        )
    })
    public ResponseEntity<TelemetryDtos.SecurityMetricsResponse> getSecurityMetrics(
        @Parameter(description = "기간 (today | 7d | 30d | 90d)", example = "30d")
        @RequestParam(value = "period", required = false, defaultValue = "30d") String period,
        @Parameter(description = "부서 필터 (all 또는 dept_id)", example = "all")
        @RequestParam(value = "dept", required = false, defaultValue = "all") String dept,
        @Parameter(description = "캐시 무시 여부", example = "false")
        @RequestParam(value = "refresh", required = false, defaultValue = "false") Boolean refresh
    ) {
        return ResponseEntity.ok(telemetryService.getSecurityMetrics(period, dept));
    }

    /**
     * 성능 지표 조회
     */
    @GetMapping("/performance")
    @Operation(
        summary = "성능 지표 조회",
        description = "불만족도, 재질문률, OOS 카운트, 지연시간 히스토그램, 모델별 평균 지연시간을 조회합니다."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "성공",
            content = @Content(schema = @Schema(implementation = TelemetryDtos.PerformanceMetricsResponse.class))
        )
    })
    public ResponseEntity<TelemetryDtos.PerformanceMetricsResponse> getPerformanceMetrics(
        @Parameter(description = "기간 (today | 7d | 30d | 90d)", example = "30d")
        @RequestParam(value = "period", required = false, defaultValue = "30d") String period,
        @Parameter(description = "부서 필터 (all 또는 dept_id)", example = "all")
        @RequestParam(value = "dept", required = false, defaultValue = "all") String dept,
        @Parameter(description = "캐시 무시 여부", example = "false")
        @RequestParam(value = "refresh", required = false, defaultValue = "false") Boolean refresh
    ) {
        return ResponseEntity.ok(telemetryService.getPerformanceMetrics(period, dept));
    }
}

