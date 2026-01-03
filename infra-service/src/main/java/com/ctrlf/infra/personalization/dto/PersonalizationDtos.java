package com.ctrlf.infra.personalization.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Personalization 관련 요청/응답 DTO 모음.
 * AI Gateway에서 개인화 데이터를 요청하는 API용 DTO입니다.
 */
public final class PersonalizationDtos {
    private PersonalizationDtos() {}

    // ---------- Resolve Request ----------
    /**
     * 개인화 facts 조회 요청.
     */
    @Getter
    @NoArgsConstructor
    public static class ResolveRequest {
        @NotBlank
        @Schema(example = "Q11", description = "인텐트 ID (Q1-Q20)")
        private String sub_intent_id;

        @Schema(example = "this-year", description = "기간 유형 (this-week, this-month, 3m, this-year)")
        private String period;

        @Schema(example = "D001", description = "부서 비교 대상 ID (Q5에서만 사용)", nullable = true)
        private String target_dept_id;
    }

    // ---------- Resolve Response ----------
    /**
     * 개인화 facts 조회 응답 (공통 구조).
     */
    @Getter
    @AllArgsConstructor
    public static class ResolveResponse {
        private String sub_intent_id;
        private String period_start;
        private String period_end;
        private String updated_at;
        private Map<String, Object> metrics;
        private List<Object> items;
        private Map<String, Object> extra;
        private ErrorInfo error;
    }

    /**
     * 에러 정보.
     */
    @Getter
    @AllArgsConstructor
    public static class ErrorInfo {
        private String type;
        private String message;
    }

    // ---------- Q1: 미이수 필수 교육 조회 ----------
    @Getter
    @AllArgsConstructor
    public static class Q1EducationItem {
        private String education_id;
        private String title;
        private String deadline;
        private String status;
    }

    @Getter
    @AllArgsConstructor
    public static class Q1Metrics {
        private int total_required;
        private int completed;
        private int remaining;
    }

    // ---------- Q3: 이번 달 데드라인 필수 교육 ----------
    @Getter
    @AllArgsConstructor
    public static class Q3EducationItem {
        private String education_id;
        private String title;
        private String deadline;
        private int days_left;
    }

    @Getter
    @AllArgsConstructor
    public static class Q3Metrics {
        private int deadline_count;
    }

    // ---------- Q5: 내 평균 vs 부서/전사 평균 ----------
    @Getter
    @AllArgsConstructor
    public static class Q5Metrics {
        private double my_average;
        private double dept_average;
        private double company_average;
    }

    @Getter
    @AllArgsConstructor
    public static class Q5Extra {
        private String target_dept_id;
        private String target_dept_name;
    }

    @Getter
    @AllArgsConstructor
    public static class Q5Item {
        private int rank;
        private String topic;
        private double wrong_rate;
    }

    // ---------- Q6: 가장 많이 틀린 보안 토픽 TOP3 ----------
    @Getter
    @AllArgsConstructor
    public static class Q6TopicItem {
        private int rank;
        private String topic;
        private double wrong_rate;
    }

    // ---------- Q9: 이번 주 교육/퀴즈 할 일 ----------
    @Getter
    @AllArgsConstructor
    public static class Q9TodoItem {
        private String type; // "education" | "quiz"
        private String title;
        private String deadline;
    }

    @Getter
    @AllArgsConstructor
    public static class Q9Metrics {
        private int todo_count;
    }

    // ---------- Q11: 남은 연차 일수 ----------
    @Getter
    @AllArgsConstructor
    public static class Q11Metrics {
        private int total_days;
        private int used_days;
        private int remaining_days;
    }

    // ---------- Q14: 복지/식대 포인트 잔액 ----------
    @Getter
    @AllArgsConstructor
    public static class Q14Metrics {
        private int welfare_points;
        private int meal_allowance;
    }

    // ---------- Q20: 올해 HR 할 일 (미완료) ----------
    @Getter
    @AllArgsConstructor
    public static class Q20TodoItem {
        private String type; // "education" | "document" | "survey" | "review"
        private String title;
        private String status; // 선택
        private String deadline; // 선택
    }

    @Getter
    @AllArgsConstructor
    public static class Q20Metrics {
        private int todo_count;
    }
}

