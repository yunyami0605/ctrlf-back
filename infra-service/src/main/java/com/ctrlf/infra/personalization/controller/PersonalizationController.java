package com.ctrlf.infra.personalization.controller;

import static com.ctrlf.infra.personalization.dto.PersonalizationDtos.*;

import com.ctrlf.infra.personalization.dto.PersonalizationDtos;
import com.ctrlf.infra.personalization.service.PersonalizationService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Personalization REST 컨트롤러.
 * AI Gateway(FastAPI)에서 개인화 데이터를 요청하는 API를 제공합니다.
 */
@Slf4j
@RestController
@RequestMapping("/api/personalization")
@RequiredArgsConstructor
@Tag(name = "Personalization", description = "개인화 데이터 조회 API")
public class PersonalizationController {

    private final PersonalizationService personalizationService;

    @PostMapping("/resolve")
    @Operation(
        summary = "개인화 facts 조회",
        description = "AI Gateway에서 개인화 데이터를 요청합니다. X-User-Id 헤더가 필수입니다.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ResolveRequest.class),
                examples = @io.swagger.v3.oas.annotations.media.ExampleObject(
                    name = "request",
                    value = "{\n  \"sub_intent_id\": \"Q11\",\n  \"period\": \"this-year\",\n  \"target_dept_id\": null\n}"
                )
            )
        )
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "개인화 데이터 조회 성공",
            content = @Content(schema = @Schema(implementation = ResolveResponse.class))
        ),
        @ApiResponse(responseCode = "400", description = "잘못된 요청 (X-User-Id 헤더 누락 또는 잘못된 인텐트 ID)"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    /**
     * 개인화 facts를 조회합니다.
     * 
     * @param xUserId 사용자 ID (X-User-Id 헤더, 필수)
     * @param request 요청 DTO (sub_intent_id, period, target_dept_id)
     * @return 개인화 facts 응답
     */
    public ResponseEntity<ResolveResponse> resolve(
        @Parameter(description = "사용자 ID (사번, emp_id 등)", required = true)
        @RequestHeader(value = "X-User-Id", required = true) String xUserId,
        @Valid @RequestBody ResolveRequest request
    ) {
        log.info("[Personalization API] 요청 수신: userId={}, sub_intent_id={}, period={}", 
            xUserId, request.getSub_intent_id(), request.getPeriod());
        
        // X-User-Id 헤더 검증
        if (xUserId == null || xUserId.isBlank()) {
            log.warn("[Personalization API] X-User-Id 헤더 누락");
            throw new IllegalArgumentException("X-User-Id 헤더가 필수입니다.");
        }

        try {
            ResolveResponse response = personalizationService.resolve(xUserId, request);
            
            if (response.getError() != null) {
                log.warn("[Personalization API] 에러 응답: userId={}, sub_intent_id={}, error={}", 
                    xUserId, request.getSub_intent_id(), response.getError().getType());
            } else {
                log.info("[Personalization API] 성공 응답: userId={}, sub_intent_id={}", 
                    xUserId, request.getSub_intent_id());
            }
            
            // 에러가 있는 경우에도 200 OK로 반환 (AI Gateway에서 error 필드로 처리)
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[Personalization API] 예외 발생: userId={}, sub_intent_id={}, error={}", 
                xUserId, request.getSub_intent_id(), e.getMessage(), e);
            throw e;
        }
    }
}

