package com.ctrlf.education.quiz.controller;

import com.ctrlf.education.quiz.dto.QuizResponse.DashboardSummaryResponse;
import com.ctrlf.education.quiz.dto.QuizResponse.DepartmentScoreResponse;
import com.ctrlf.education.quiz.dto.QuizResponse.QuizStatsResponse;
import com.ctrlf.education.quiz.service.QuizService;
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

@Tag(name = "Quiz-Admin", description = "퀴즈 관리자 대시보드 통계 API (ADMIN)")
@RestController
@RequestMapping("/admin/dashboard/quiz")
@SecurityRequirement(name = "bearer-jwt")
@RequiredArgsConstructor
public class AdminQuizController {

    private final QuizService quizService;

    @GetMapping("/summary")
    @Operation(
        summary = "대시보드 요약 통계 조회",
        description = "전체 평균 점수, 응시자 수, 통과율(80점↑), 퀴즈 응시율을 조회합니다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "성공",
            content = @Content(schema = @Schema(implementation = DashboardSummaryResponse.class)))
    })
    public ResponseEntity<DashboardSummaryResponse> getDashboardSummary(
        @Parameter(description = "기간 (일수, 7/30/90)", example = "30")
        @RequestParam(value = "period", required = false) Integer period,
        @Parameter(description = "부서 필터", example = "총무팀")
        @RequestParam(value = "department", required = false) String department) {
        return ResponseEntity.ok(quizService.getDashboardSummary(period, department));
    }

    @GetMapping("/department-scores")
    @Operation(
        summary = "부서별 평균 점수 조회",
        description = "부서별 평균 점수와 응시자 수를 조회합니다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "성공",
            content = @Content(schema = @Schema(implementation = DepartmentScoreResponse.class))
        )
    })
    public ResponseEntity<DepartmentScoreResponse> getDepartmentScores(
        @Parameter(description = "기간 (일수, 7/30/90)", example = "30")
        @RequestParam(value = "period", required = false) Integer period,
        @Parameter(description = "부서 필터", example = "총무팀")
        @RequestParam(value = "department", required = false) String department) {
        return ResponseEntity.ok(quizService.getDepartmentScores(period, department));
    }

    @GetMapping("/quiz-stats")
    @Operation(
        summary = "퀴즈별 통계 조회",
        description = "퀴즈 제목, 회차, 평균 점수, 응시 수, 통과율을 조회합니다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "성공",
            content = @Content(schema = @Schema(implementation = QuizStatsResponse.class))
        )
    })
    public ResponseEntity<QuizStatsResponse> getQuizStats(
        @Parameter(description = "기간 (일수, 7/30/90)", example = "30")
        @RequestParam(value = "period", required = false) Integer period,
        @Parameter(description = "부서 필터", example = "총무팀")
        @RequestParam(value = "department", required = false) String department) {
        return ResponseEntity.ok(quizService.getQuizStats(period, department));
    }
}

