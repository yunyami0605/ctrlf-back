package com.ctrlf.infra.telemetry.controller;

import com.ctrlf.infra.telemetry.dto.TelemetryDtos;
import com.ctrlf.infra.telemetry.service.TelemetryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 텔레메트리 수집 API 컨트롤러
 * 
 * <p>AI에서 생성한 구조화 이벤트를 수집합니다.</p>
 */
@Tag(name = "Telemetry", description = "텔레메트리 이벤트 수집 API (Internal)")
@RestController
@RequestMapping("/internal/telemetry")
@RequiredArgsConstructor
public class TelemetryController {

    private final TelemetryService telemetryService;

    /**
     * 텔레메트리 이벤트 수집
     * 
     * <p>AI가 생성한 구조화 이벤트를 배치로 수집합니다.</p>
     * <p>Idempotent: 중복 eventId는 무시됩니다.</p>
     */
    @PostMapping("/events")
    @Operation(
        summary = "텔레메트리 이벤트 수집",
        description = "AI에서 생성한 구조화 이벤트를 배치로 수집합니다. 중복 eventId는 자동으로 무시됩니다."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "수신 완료 (부분 실패 포함)",
            content = @Content(schema = @Schema(implementation = TelemetryDtos.TelemetryEventResponse.class))
        ),
        @ApiResponse(responseCode = "401", description = "토큰 오류"),
        @ApiResponse(responseCode = "403", description = "권한 오류"),
        @ApiResponse(responseCode = "413", description = "Payload 너무 큼"),
        @ApiResponse(responseCode = "429", description = "레이트 제한"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<TelemetryDtos.TelemetryEventResponse> collectEvents(
        @Parameter(description = "내부 인증 토큰", required = true)
        @RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
        @Valid @RequestBody TelemetryDtos.TelemetryEventRequest request
    ) {
        // TODO: X-Internal-Token 검증 로직 추가
        TelemetryDtos.TelemetryEventResponse response = telemetryService.collectEvents(request);
        return ResponseEntity.ok(response);
    }
}

