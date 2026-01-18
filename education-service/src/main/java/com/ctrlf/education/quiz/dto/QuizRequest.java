package com.ctrlf.education.quiz.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 퀴즈 요청 DTO 클래스.
 */
public final class QuizRequest {
    private QuizRequest() {}

    
    /**
     * 퀴즈 제출 요청.
     */
    @Getter
    @NoArgsConstructor
    public static class SubmitRequest {
        /** 답안 목록 */
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private List<AnswerItem> answers;
    }

    /**
     * 답안 항목.
     */
    @Getter
    @NoArgsConstructor
    public static class AnswerItem {
        /** 문항 ID */
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private UUID questionId;
        /** 사용자가 선택한 답안 인덱스 (0-based) */
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private Integer userSelectedIndex;
    }

    
    /**
     * 퀴즈 이탈 기록 요청.
     */
    @Getter
    @NoArgsConstructor
    public static class LeaveRequest {
        /** 이탈 시각 */
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2025-01-01T12:00:00Z")
        private Instant timestamp;
        /** 이탈 사유 */
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "window_blur")
        private String reason;
        @Schema(description = "이탈 시간(초). 프론트에서 이탈 시작 시각과 복귀 시각의 차이를 계산하여 전송", example = "30")
        private Integer leaveSeconds;
    }

    
    /**
     * 퀴즈 답안 임시 저장 요청.
     */
    @Getter
    @NoArgsConstructor
    public static class SaveRequest {
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "임시 저장할 답안 목록")
        private List<AnswerItem> answers;
    }
}

