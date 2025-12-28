package com.ctrlf.education.quiz.controller;

import static com.ctrlf.education.quiz.dto.QuizRequest.*;
import static com.ctrlf.education.quiz.dto.QuizResponse.*;
import com.ctrlf.education.quiz.dto.QuizResponse.AvailableEducationItem;
import com.ctrlf.education.quiz.dto.QuizResponse.DepartmentStatsItem;
import com.ctrlf.education.quiz.dto.QuizResponse.MyAttemptItem;
import com.ctrlf.education.quiz.dto.QuizResponse.RetryInfoResponse;

import com.ctrlf.common.security.SecurityUtils;
import java.util.List;
import com.ctrlf.education.quiz.service.QuizService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/quiz")
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;

    @GetMapping("/{eduId}/start")
    @Operation(summary = "퀴즈 시작(문항 생성/복원) (프론트 -> 백엔드)")
    public ResponseEntity<StartResponse> start(
        @PathVariable("eduId") UUID educationId,
        @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userUuid = SecurityUtils.extractUserUuid(jwt)
            .orElseThrow(() -> new IllegalArgumentException("사용자 UUID를 추출할 수 없습니다."));
        return ResponseEntity.ok(quizService.start(educationId, userUuid));
    }

    @PostMapping("/attempt/{attemptId}/submit")
    @Operation(summary = "퀴즈 제출/채점 (프론트 -> 백엔드)")
    public ResponseEntity<SubmitResponse> submit(
        @PathVariable("attemptId") UUID attemptId,
        @AuthenticationPrincipal Jwt jwt,
        @RequestBody SubmitRequest req
    ) {
        UUID userUuid = SecurityUtils.extractUserUuid(jwt)
            .orElseThrow(() -> new IllegalArgumentException("사용자 UUID를 추출할 수 없습니다."));
        // JWT에서 부서 정보 추출 (첫 번째 부서 사용)
        List<String> departments = SecurityUtils.extractDepartments(jwt);
        String department = departments.isEmpty() ? null : departments.get(0);
        return ResponseEntity.ok(quizService.submit(attemptId, userUuid, department, req));
    }

    @GetMapping("/attempt/{attemptId}/result")
    @Operation(summary = "퀴즈 결과 조회 (프론트 -> 백엔드)")
    public ResponseEntity<ResultResponse> result(
        @PathVariable("attemptId") UUID attemptId,
        @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userUuid = SecurityUtils.extractUserUuid(jwt)
            .orElseThrow(() -> new IllegalArgumentException("사용자 UUID를 추출할 수 없습니다."));
        return ResponseEntity.ok(quizService.result(attemptId, userUuid));
    }

    @GetMapping("/{attemptId}/wrongs")
    @Operation(summary = "오답노트 목록 조회 (프론트 -> 백엔드)")
    public ResponseEntity<List<WrongNoteItem>> wrongs(
        @PathVariable("attemptId") UUID attemptId,
        @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userUuid = SecurityUtils.extractUserUuid(jwt)
            .orElseThrow(() -> new IllegalArgumentException("사용자 UUID를 추출할 수 없습니다."));
        return ResponseEntity.ok(quizService.wrongs(attemptId, userUuid));
    }

    @PostMapping("/attempt/{attemptId}/leave")
    @Operation(summary = "퀴즈 이탈 기록 (프론트 -> 백엔드)")
    public ResponseEntity<LeaveResponse> leave(
        @PathVariable("attemptId") UUID attemptId,
        @AuthenticationPrincipal Jwt jwt,
        @RequestBody LeaveRequest req
    ) {
        UUID userUuid = SecurityUtils.extractUserUuid(jwt)
            .orElseThrow(() -> new IllegalArgumentException("사용자 UUID를 추출할 수 없습니다."));
        return ResponseEntity.ok(quizService.leave(attemptId, userUuid, req));
    }

    @PostMapping("/attempt/{attemptId}/save")
    @Operation(summary = "응답 임시 저장 (프론트 -> 백엔드)", description = "FS-QUIZ-PLAY-03: 진행 중인 답안 임시 저장 (페이지 새로고침/이탈 시 복구용)")
    public ResponseEntity<SaveResponse> save(
        @PathVariable("attemptId") UUID attemptId,
        @AuthenticationPrincipal Jwt jwt,
        @RequestBody SaveRequest req
    ) {
        UUID userUuid = SecurityUtils.extractUserUuid(jwt)
            .orElseThrow(() -> new IllegalArgumentException("사용자 UUID를 추출할 수 없습니다."));
        return ResponseEntity.ok(quizService.save(attemptId, userUuid, req));
    }

    @GetMapping("/attempt/{attemptId}/timer")
    @Operation(summary = "타이머 정보 조회 (프론트 -> 백엔드)", description = "FS-QUIZ-PLAY-02: 시간 제한, 남은 시간, 시작 시각 조회")
    public ResponseEntity<TimerResponse> getTimer(
        @PathVariable("attemptId") UUID attemptId,
        @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userUuid = SecurityUtils.extractUserUuid(jwt)
            .orElseThrow(() -> new IllegalArgumentException("사용자 UUID를 추출할 수 없습니다."));
        return ResponseEntity.ok(quizService.getTimer(attemptId, userUuid));
    }

    @GetMapping("/available-educations")
    @Operation(summary = "풀 수 있는 퀴즈 목록 조회 (이수 완료한 교육 기준) (프론트 -> 백엔드)", description = "FS-QUIZ-START-01: 이수 완료한 교육만 리스트에 노출")
    public ResponseEntity<List<AvailableEducationItem>> getAvailableEducations(
        @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userUuid = SecurityUtils.extractUserUuid(jwt)
            .orElseThrow(() -> new IllegalArgumentException("사용자 UUID를 추출할 수 없습니다."));
        return ResponseEntity.ok(quizService.getAvailableEducations(userUuid));
    }

    @GetMapping("/my-attempts")
    @Operation(summary = "내가 풀었던 퀴즈 응시 내역 조회 (프론트 -> 백엔드)", description = "FS-MYPAGE-03: 응시한 퀴즈 목록, 점수, 통과 여부, 교육별 최고 점수")
    public ResponseEntity<List<MyAttemptItem>> getMyAttempts(
        @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userUuid = SecurityUtils.extractUserUuid(jwt)
            .orElseThrow(() -> new IllegalArgumentException("사용자 UUID를 추출할 수 없습니다."));
        return ResponseEntity.ok(quizService.getMyAttempts(userUuid));
    }

    @GetMapping("/department-stats")
    @Operation(
        summary = "부서별 퀴즈 통계 조회 (프론트 -> 백엔드)",
        description = "부서별 평균 점수와 진행률을 조회합니다. educationId가 없으면 전체 교육 대상으로 계산합니다."
    )
    public ResponseEntity<List<DepartmentStatsItem>> getDepartmentStats(
        @org.springframework.web.bind.annotation.RequestParam(required = false) UUID educationId
    ) {
        return ResponseEntity.ok(quizService.getDepartmentStats(educationId));
    }

    @GetMapping("/{eduId}/retry-info")
    @Operation(
        summary = "퀴즈 재응시 정보 조회 (프론트 -> 백엔드)",
        description = "특정 교육에 대한 재응시 가능 여부 및 관련 정보를 조회합니다. (응시 횟수, 최고 점수, 통과 여부 등)"
    )
    public ResponseEntity<RetryInfoResponse> getRetryInfo(
        @PathVariable("eduId") UUID educationId,
        @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userUuid = SecurityUtils.extractUserUuid(jwt)
            .orElseThrow(() -> new IllegalArgumentException("사용자 UUID를 추출할 수 없습니다."));
        return ResponseEntity.ok(quizService.getRetryInfo(educationId, userUuid));
    }
}

