package com.ctrlf.education.quiz.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 퀴즈 응답 DTO 클래스.
 */
public final class QuizResponse {
    private QuizResponse() {}

    
    /**
     * 퀴즈 시작 응답.
     */
    @Getter
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StartResponse {
        /** 시도 ID */
        private UUID attemptId;
        /** 문항 목록 */
        private List<QuestionItem> questions;
    }

    /**
     * 문항 항목.
     */
    @Getter
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class QuestionItem {
        /** 문항 ID */
        private UUID questionId;
        /** 문항 순서 */
        private Integer order;
        /** 문항 내용 */
        private String question;
        /** 선택지 목록 */
        private List<String> choices;
        /** 정답 인덱스 (제출 전까지는 null) */
        private Integer answerIndex;
    }
    /**
     * 퀴즈 제출 응답.
     */
    @Getter
    @AllArgsConstructor
    public static class SubmitResponse {
        /** 점수 */
        private int score;
        /** 통과 여부 */
        private boolean passed;
        /** 정답 개수 */
        private int correctCount;
        /** 오답 개수 */
        private int wrongCount;
        /** 전체 문항 수 */
        private int totalCount;
        /** 제출 시각 */
        private Instant submittedAt;
    }
    /**
     * 퀴즈 결과 조회 응답.
     */
    @Getter
    @AllArgsConstructor
    public static class ResultResponse {
        /** 점수 */
        private int score;
        /** 통과 여부 */
        private boolean passed;
        /** 통과 기준 점수 */
        private Integer passScore;
        /** 정답 개수 */
        private int correctCount;
        /** 오답 개수 */
        private int wrongCount;
        /** 전체 문항 수 */
        private int totalCount;
        /** 완료 시각 */
        private Instant finishedAt;
    }
    /**
     * 오답노트 항목.
     */
    @Getter
    @AllArgsConstructor
    public static class WrongNoteItem {
        /** 문항 내용 */
        private String question;
        /** 사용자가 선택한 답안 인덱스 */
        private Integer userAnswerIndex;
        /** 정답 인덱스 */
        private Integer correctAnswerIndex;
        /** 해설 */
        private String explanation;
        /** 선택지 목록 */
        private List<String> choices;
    }
    /**
     * 퀴즈 이탈 기록 응답.
     */
    @Getter
    @AllArgsConstructor
    public static class LeaveResponse {
        /** 기록 성공 여부 */
        private boolean recorded;
        /** 총 이탈 횟수 */
        private int leaveCount;
        /** 마지막 이탈 시각 */
        private Instant lastLeaveAt;
    }
    /**
     * 퀴즈 타이머 조회 응답.
     */
    @Getter
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TimerResponse {
        /** 시간 제한(초), null이면 제한 없음 */
        private Integer timeLimit;
        /** 시작 시각 */
        private Instant startedAt;
        /** 만료 시각 (startedAt + timeLimit), null이면 제한 없음 */
        private Instant expiresAt;
        /** 남은 시간(초), null이면 제한 없음 또는 이미 만료 */
        private Long remainingSeconds;
        /** 만료 여부 */
        private Boolean isExpired;
    }
    /**
     * 퀴즈 답안 임시 저장 응답.
     */
    @Getter
    @AllArgsConstructor
    public static class SaveResponse {
        /** 저장 성공 여부 */
        private boolean saved;
        /** 저장된 답안 개수 */
        private int savedCount;
        /** 저장 시각 */
        private Instant savedAt;
    }
    /**
     * 응시 가능한 교육 항목.
     */
    @Getter
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AvailableEducationItem {
        /** 교육 ID */
        private UUID educationId;
        /** 교육 제목 */
        private String title;
        /** 카테고리 */
        private String category;
        /** 교육 유형 */
        private String eduType;
        /** 기존 응시 횟수 */
        private Integer attemptCount;
        /** 최대 응시 횟수 (null이면 무제한) */
        private Integer maxAttempts;
        /** 이미 응시한 퀴즈인지 여부 (true: 풀었음, false: 아직 안 풀었음) */
        private Boolean hasAttempted;
        /** 최고 점수 (응시한 경우에만, null이면 미응시) */
        private Integer bestScore;
        /** 통과 여부 (응시한 경우에만, null이면 미응시) */
        private Boolean passed;
    }
    /**
     * 내 시도 목록 항목.
     */
    @Getter
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MyAttemptItem {
        /** 시도 ID */
        private UUID attemptId;
        /** 교육 ID */
        private UUID educationId;
        /** 교육 제목 */
        private String educationTitle;
        /** 점수 */
        private Integer score;
        /** 통과 여부 */
        private Boolean passed;
        /** 시도 회차 */
        private Integer attemptNo;
        /** 제출 시각 */
        private Instant submittedAt;
        /** 교육별 최고 점수 여부 */
        private Boolean isBestScore;
    }
    /**
     * 부서별 통계 항목.
     */
    @Getter
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DepartmentStatsItem {
        /** 부서명 (예: "인사팀", "총무팀") */
        private String departmentName;
        /** 부서 평균 점수 */
        private Integer averageScore;
        /** 부서 전체 진행률 (%) */
        private Integer progressPercent;
        /** 참여자 수 */
        private Integer participantCount;
    }
    /**
     * 재응시 정보 응답.
     */
    @Getter
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RetryInfoResponse {
        /** 교육 ID */
        private UUID educationId;
        /** 교육 제목 */
        private String educationTitle;
        /** 재응시 가능 여부 */
        private Boolean canRetry;
        /** 현재 응시 횟수 */
        private Integer currentAttemptCount;
        /** 최대 응시 횟수 (null이면 무제한) */
        private Integer maxAttempts;
        /** 남은 응시 횟수 (null이면 무제한) */
        private Integer remainingAttempts;
        /** 최고 점수 (응시한 경우) */
        private Integer bestScore;
        /** 통과 여부 (최고 점수 시도 기준) */
        private Boolean passed;
        /** 마지막 응시 시각 */
        private Instant lastAttemptAt;
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

    // ========================
    // 내부 API (Internal) 관련 DTOs
    // ========================

    /**
     * 토픽별 퀴즈 점수 항목.
     */
    @Getter
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @lombok.Builder
    public static class TopicScoreItem {
        /** 교육 ID */
        private String educationId;
        /** 교육 제목 */
        private String title;
        /** 응시 여부 */
        private boolean hasAttempt;
        /** 최고 점수 */
        private Integer bestScore;
        /** 통과 여부 */
        private Boolean passed;
        /** 응시 횟수 */
        private Integer attemptCount;
        /** 통과 기준 점수 */
        private Integer passScore;
        /** 마지막 응시 시각 */
        private String lastAttemptAt;
    }

    /**
     * 토픽별 퀴즈 점수 응답.
     */
    @Getter
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @lombok.Builder
    public static class TopicScoreResponse {
        /** 토픽 코드 */
        private String topic;
        /** 토픽 한글 라벨 */
        private String topicLabel;
        /** 교육 개수 */
        private int educationCount;
        /** 응시한 교육 개수 */
        private int attemptedCount;
        /** 통과한 교육 개수 */
        private int passedCount;
        /** 응시 여부 */
        private boolean hasAttempt;
        /** 평균 점수 */
        private Double averageScore;
        /** 교육별 점수 항목 목록 */
        private List<TopicScoreItem> items;
    }
}

