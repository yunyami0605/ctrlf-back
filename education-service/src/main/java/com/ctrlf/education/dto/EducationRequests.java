package com.ctrlf.education.dto;

import com.ctrlf.education.entity.EducationCategory;
import com.ctrlf.education.entity.EducationTopic;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 교육 도메인 요청 DTO 묶음.
 */
public final class EducationRequests {
    private EducationRequests() {}
    /**
     * 교육 생성 요청 DTO.
     * 제목/카테고리/필수 여부 등의 메타 정보를 포함합니다.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class CreateEducationRequest {
        // 교육 제목 (필수)
        @NotBlank
        private String title;
        // 교육 설명
        private String description;
        // 주제 카테고리 (필수)
        @NotNull
        @Schema(description = "주제 카테고리", example = "JOB_DUTY")
        private EducationTopic category;
        // 교육 유형
        @Schema(description = "교육 유형", example = "MANDATORY")
        private EducationCategory eduType;
        // 필수 교육인지 여부
        @NotNull
        private Boolean require;
        // 해당 교육 통과 기준 점수
        private Integer passScore;
        // 통과 기준 비율 (옵션, %)
        private Integer passRatio;
    }

    /**
     * 교육 수정 요청 DTO.
     * 부분 업데이트를 허용합니다.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class UpdateEducationRequest {
        private String title;
        private String description;
        // 주제 카테고리
        @Schema(description = "주제 카테고리")
        private EducationTopic category;
        // 교육 유형
        @Schema(description = "교육 유형")
        private EducationCategory eduType;
        private Boolean require;
        // 통과 기준 점수
        private Integer passScore;
        // 통과 기준 비율(%)
        private Integer passRatio;
    }

    /**
     * 영상 진행률 업데이트 요청 DTO.
     * 재생 위치/영상 길이/시청 시간(증분)을 포함합니다.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class VideoProgressUpdateRequest {
        // 현재 재생 위치(초)
        private Integer position;
        // 시청 시간 증가분(초)
        private Integer watchTime;
    }
}

