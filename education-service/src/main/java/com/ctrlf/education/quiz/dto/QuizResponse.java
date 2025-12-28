package com.ctrlf.education.quiz.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

public final class QuizResponse {
    private QuizResponse() {}

    // ---------- Start (GET /quiz/{eduId}/start) ----------
    @Getter
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StartResponse {
        private UUID attemptId;
        private List<QuestionItem> questions;
    }

    @Getter
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class QuestionItem {
        private UUID questionId;
        private Integer order; // 문항 순서 (0-based)
        private String question;
        private List<String> choices;
        private Integer answerIndex; // null until submitted
    }

    // ---------- Submit (POST /quiz/attempt/{attemptId}/submit) ----------
    @Getter
    @AllArgsConstructor
    public static class SubmitResponse {
        private int score;
        private boolean passed;
        private int correctCount;
        private int wrongCount;
        private int totalCount;
        private Instant submittedAt;
    }

    // ---------- Result (GET /quiz/attempt/{attemptId}/result) ----------
    @Getter
    @AllArgsConstructor
    public static class ResultResponse {
        private int score;
        private boolean passed;
        private Integer passScore; // 통과 기준 점수
        private int correctCount;
        private int wrongCount;
        private int totalCount;
        private Instant finishedAt;
    }

    // ---------- Wrongs (GET /quiz/{attemptId}/wrongs) ----------
    @Getter
    @AllArgsConstructor
    public static class WrongNoteItem {
        private String question;
        private Integer userAnswerIndex;
        private Integer correctAnswerIndex;
        private String explanation;
        private List<String> choices;
    }

    // ---------- Leave (POST /quiz/attempt/{attemptId}/leave) ----------
    @Getter
    @AllArgsConstructor
    public static class LeaveResponse {
        private boolean recorded;
        private int leaveCount;
        private Instant lastLeaveAt;
    }

    // ---------- Timer (GET /quiz/attempt/{attemptId}/timer) ----------
    @Getter
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TimerResponse {
        private Integer timeLimit; // 시간 제한(초), null이면 제한 없음
        private Instant startedAt; // 시작 시각
        private Instant expiresAt; // 만료 시각 (startedAt + timeLimit), null이면 제한 없음
        private Long remainingSeconds; // 남은 시간(초), null이면 제한 없음 또는 이미 만료
        private Boolean isExpired; // 만료 여부
    }

    // ---------- Save (POST /quiz/attempt/{attemptId}/save) ----------
    @Getter
    @AllArgsConstructor
    public static class SaveResponse {
        private boolean saved; // 저장 성공 여부
        private int savedCount; // 저장된 답안 개수
        private Instant savedAt; // 저장 시각
    }

    // ---------- Available Educations (GET /quiz/available-educations) ----------
    @Getter
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AvailableEducationItem {
        private UUID educationId;
        private String title;
        private String category;
        private String eduType;
        private Integer attemptCount; // 기존 응시 횟수
        private Integer maxAttempts; // 최대 응시 횟수 (null이면 무제한)
        private Boolean hasAttempted; // 이미 응시한 퀴즈인지 여부 (true: 풀었음, false: 아직 안 풀었음)
        private Integer bestScore; // 최고 점수 (응시한 경우에만, null이면 미응시)
        private Boolean passed; // 통과 여부 (응시한 경우에만, null이면 미응시)
    }

    // ---------- My Attempts (GET /quiz/my-attempts) ----------
    @Getter
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MyAttemptItem {
        private UUID attemptId;
        private UUID educationId;
        private String educationTitle;
        private Integer score;
        private Boolean passed;
        private Integer attemptNo;
        private Instant submittedAt;
        private Boolean isBestScore; // 교육별 최고 점수 여부
    }

    // ---------- Department Stats (GET /quiz/department-stats) ----------
    @Getter
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DepartmentStatsItem {
        private String departmentName; // 부서명 (예: "인사팀", "총무팀")
        private Integer averageScore; // 부서 평균 점수
        private Integer progressPercent; // 부서 전체 진행률 (%)
        private Integer participantCount; // 참여자 수
    }

    // ---------- Retry Info (GET /quiz/{eduId}/retry-info) ----------
    @Getter
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RetryInfoResponse {
        private UUID educationId;
        private String educationTitle;
        private Boolean canRetry; // 재응시 가능 여부
        private Integer currentAttemptCount; // 현재 응시 횟수
        private Integer maxAttempts; // 최대 응시 횟수 (null이면 무제한)
        private Integer remainingAttempts; // 남은 응시 횟수 (null이면 무제한)
        private Integer bestScore; // 최고 점수 (응시한 경우)
        private Boolean passed; // 통과 여부 (최고 점수 시도 기준)
        private Instant lastAttemptAt; // 마지막 응시 시각
    }

    // ========================
    // 관리자 대시보드 통계 관련 DTOs
    // ========================

    /**
     * 대시보드 요약 통계 응답.
     */
    @Getter
    @AllArgsConstructor
    public static class DashboardSummaryResponse {
        /** 전체 평균 점수 */
        private Double overallAverageScore;
        /** 응시자 수 */
        private Long participantCount;
        /** 통과율 (80점↑) (%) */
        private Double passRate;
        /** 퀴즈 응시율 (%) */
        private Double participationRate;
    }

    /**
     * 부서별 평균 점수 항목.
     */
    @Getter
    @AllArgsConstructor
    public static class DepartmentScoreItem {
        /** 부서명 */
        private String department;
        /** 평균 점수 */
        private Double averageScore;
        /** 응시자 수 */
        private Long participantCount;
    }

    /**
     * 부서별 평균 점수 응답.
     */
    @Getter
    @AllArgsConstructor
    public static class DepartmentScoreResponse {
        private List<DepartmentScoreItem> items;
    }

    /**
     * 퀴즈별 통계 항목.
     */
    @Getter
    @AllArgsConstructor
    public static class QuizStatsItem {
        /** 교육 ID */
        private UUID educationId;
        /** 퀴즈 제목 (교육 제목) */
        private String quizTitle;
        /** 회차 */
        private Integer attemptNo;
        /** 평균 점수 */
        private Double averageScore;
        /** 응시 수 */
        private Long attemptCount;
        /** 통과율 (%) */
        private Double passRate;
    }

    /**
     * 퀴즈별 통계 응답.
     */
    @Getter
    @AllArgsConstructor
    public static class QuizStatsResponse {
        private List<QuizStatsItem> items;
    }
}

