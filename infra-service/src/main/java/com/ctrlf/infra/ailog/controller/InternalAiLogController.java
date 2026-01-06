package com.ctrlf.infra.ailog.controller;

import com.ctrlf.infra.ailog.dto.AiLogDtos;
import com.ctrlf.infra.ailog.service.AiLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 로그 수신 API 컨트롤러 (내부 API)
 * 
 * <p>AI에서 정제된 로그를 bulk로 수신합니다.</p>
 */
@Slf4j
@Tag(name = "AI-Logs-Internal", description = "AI 로그 수신 API (Internal)")
@RestController
@RequestMapping("/internal/ai/logs")
@RequiredArgsConstructor
public class InternalAiLogController {

    private final AiLogService aiLogService;

    /**
     * AI 로그 Bulk 수신
     * 
     * <p>AI에서 정제된 로그를 bulk로 수신하여 DB에 저장합니다.</p>
     */
    @PostMapping("/bulk")
    @Operation(
        summary = "AI 로그 Bulk 수신",
        description = "AI에서 정제된 로그를 bulk로 수신하여 DB에 저장합니다."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "수신 완료 (부분 실패 포함)",
            content = @Content(schema = @Schema(implementation = AiLogDtos.BulkResponse.class))
        ),
        @ApiResponse(responseCode = "400", description = "잘못된 요청"),
        @ApiResponse(responseCode = "401", description = "내부 토큰 오류"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<AiLogDtos.BulkResponse> bulkReceiveLogs(
        @Parameter(description = "내부 인증 토큰", required = true)
        @RequestHeader(value = "X-Internal-Token") String internalToken,
        @Valid @RequestBody AiLogDtos.BulkRequest request
    ) {
        // X-Internal-Token 검증은 InternalTokenFilter에서 처리
        log.info("[AI 로그 Bulk 수신] 요청 수신: logs 개수={}", request.getLogs() != null ? request.getLogs().size() : 0);
        
        AiLogDtos.BulkResponse response = aiLogService.saveBulkLogs(request);
        
        log.info("[AI 로그 Bulk 수신] 처리 완료: received={}, saved={}, failed={}", 
            response.getReceived(), response.getSaved(), response.getFailed());
        
        return ResponseEntity.ok(response);
    }
}

