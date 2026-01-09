package com.ctrlf.chat.controller;

import com.ctrlf.chat.dto.response.ChatDashboardResponse;
import com.ctrlf.chat.dto.response.ChatLogDtos;
import com.ctrlf.chat.elasticsearch.service.ChatLogElasticsearchService;
import com.ctrlf.chat.service.ChatDashboardService;
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
 * 챗봇 관리자 대시보드 통계 API 컨트롤러
 * 
 * <p>관리자가 챗봇 사용 현황을 모니터링하고 통계를 조회하는 API를 제공합니다.</p>
 * 
 * @author CtrlF Team
 * @since 1.0.0
 */
@Tag(name = "Chat-Admin", description = "챗봇 관리자 대시보드 통계 API (ADMIN)")
@RestController
@RequestMapping("/admin/dashboard/chat")
@SecurityRequirement(name = "bearer-jwt")
@RequiredArgsConstructor
public class AdminChatDashboardController {

    private final ChatDashboardService chatDashboardService;
    private final ChatLogElasticsearchService chatLogElasticsearchService;

    @GetMapping("/summary")
    @Operation(
        summary = "대시보드 요약 통계 조회",
        description = "챗봇 탭 상단 요약 카드 데이터를 조회합니다."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "성공",
            content = @Content(schema = @Schema(implementation = ChatDashboardResponse.DashboardSummaryResponse.class))
        )
    })
    public ResponseEntity<ChatDashboardResponse.DashboardSummaryResponse> getDashboardSummary(
        @Parameter(description = "기간 (today | 7d | 30d | 90d)", example = "30d")
        @RequestParam(value = "period", required = false, defaultValue = "30d") String period,
        @Parameter(description = "부서 필터 (all 또는 dept_id)", example = "all")
        @RequestParam(value = "dept", required = false, defaultValue = "all") String dept,
        @Parameter(description = "캐시 무시 여부", example = "false")
        @RequestParam(value = "refresh", required = false, defaultValue = "false") Boolean refresh
    ) {
        return ResponseEntity.ok(chatDashboardService.getDashboardSummary(period, dept, refresh));
    }

    @GetMapping("/trends")
    @Operation(
        summary = "질문 수 · 에러율 추이 조회",
        description = "질문 수와 에러율 추이를 조회합니다."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "성공",
            content = @Content(schema = @Schema(implementation = ChatDashboardResponse.TrendsResponse.class))
        )
    })
    public ResponseEntity<ChatDashboardResponse.TrendsResponse> getTrends(
        @Parameter(description = "기간 (today | 7d | 30d | 90d)", example = "30d")
        @RequestParam(value = "period", required = false, defaultValue = "30d") String period,
        @Parameter(description = "부서 필터 (all 또는 dept_id)", example = "all")
        @RequestParam(value = "dept", required = false, defaultValue = "all") String dept,
        @Parameter(description = "버킷 타입 (day | week)", example = "week")
        @RequestParam(value = "bucket", required = false, defaultValue = "week") String bucket,
        @Parameter(description = "캐시 무시 여부", example = "false")
        @RequestParam(value = "refresh", required = false, defaultValue = "false") Boolean refresh
    ) {
        return ResponseEntity.ok(chatDashboardService.getTrends(period, dept, bucket, refresh));
    }

    @GetMapping("/domain-share")
    @Operation(
        summary = "도메인별 질문 비율 조회",
        description = "도메인별 질문 비율(규정/FAQ/교육/퀴즈/기타)을 조회합니다."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "성공",
            content = @Content(schema = @Schema(implementation = ChatDashboardResponse.DomainShareResponse.class))
        )
    })
    public ResponseEntity<ChatDashboardResponse.DomainShareResponse> getDomainShare(
        @Parameter(description = "기간 (today | 7d | 30d | 90d)", example = "30d")
        @RequestParam(value = "period", required = false, defaultValue = "30d") String period,
        @Parameter(description = "부서 필터 (all 또는 dept_id)", example = "all")
        @RequestParam(value = "dept", required = false, defaultValue = "all") String dept,
        @Parameter(description = "캐시 무시 여부", example = "false")
        @RequestParam(value = "refresh", required = false, defaultValue = "false") Boolean refresh
    ) {
        return ResponseEntity.ok(chatDashboardService.getDomainShare(period, dept, refresh));
    }

    /**
     * 채팅 로그 목록 조회
     * 
     * <p>Elasticsearch chat_log 인덱스에서 채팅 로그를 조회합니다.</p>
     */
    @GetMapping("/logs")
    @Operation(
        summary = "채팅 로그 목록 조회",
        description = "Elasticsearch chat_log 인덱스에서 채팅 로그를 필터링 및 페이징하여 조회합니다."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "성공",
            content = @Content(schema = @Schema(implementation = ChatLogDtos.PageResponse.class))
        ),
        @ApiResponse(responseCode = "401", description = "인증 실패"),
        @ApiResponse(responseCode = "403", description = "권한 없음"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<ChatLogDtos.PageResponse<ChatLogDtos.ChatLogItem>> getChatLogs(
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
        return ResponseEntity.ok(chatLogElasticsearchService.getChatLogs(
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

