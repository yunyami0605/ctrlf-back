package com.ctrlf.education.dto;

import com.ctrlf.education.entity.EducationCategory;
import com.ctrlf.education.entity.EducationTopic;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 교육 도메인 응답 DTO 묶음.
 */
public final class EducationResponses {
    private EducationResponses() {}

    /**
     * 교육 생성 응답 DTO.
     * 생성된 교육 식별자를 반환합니다.
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateEducationResponse {
        /** 생성된 교육 ID */
        private UUID eduId;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateEducationResponse{
        private UUID eduId;
    }

    /**
     * 교육 상세 응답 DTO.
     * 기본 정보와 섹션(차시) 목록을 포함합니다.
     */
    @Getter
    @Builder
    public static class EducationDetailResponse {
        private UUID id;
        private String title;
        private String description;
        private EducationTopic category;
        private EducationCategory eduType;
        private Boolean require;
        private Integer passScore;
        private Integer passRatio;
        /** 교육 전체 길이(초 단위). 섹션 합산 기준 */
        private Integer duration;
        private Instant createdAt;
        private Instant updatedAt;
        private List<Section> sections;

        /** 교육 섹션(차시) 요약. */
        @Getter
        @AllArgsConstructor
        public static class Section {
            private UUID id;
            private String title;
        }
    }

    /**
     * 교육 영상 목록 응답 DTO.
     * 영상별 재생 URL과 사용자 진행 정보(옵션)를 포함합니다.
     */
    @Getter
    @Builder
    public static class EducationVideosResponse {
        private UUID id;
        private String title;
        private List<VideoItem> videos;

        /** 교육 영상 항목. */
        @Getter
        @AllArgsConstructor
        public static class VideoItem {
            /** 영상 ID */
            private UUID id;
            /** 영상 제목 */
            private String title;
            /** 영상 파일 URL */
            private String fileUrl;
            /** 영상 길이(초) */
            private Integer duration;
            /** 영상 버전 */
            private Integer version;
            /** 수강 가능한 부서 목록(JSON) */
            private String departmentScope;
            /** 사용자 이어보기 위치(초) */
            private Integer resumePosition;
            /** 사용자 영상 이수 여부 */
            private Boolean isCompleted;
            /** 사용자 누적 시청 시간(초) */
            private Integer totalWatchSeconds;
            /** 진행률(%) - 서버 계산 필드 */
            private Integer progressPercent;
            /** 시청 상태 레이블(시청전/시청중/시청완료) */
            private String watchStatus;
        }
    }

    /**
     * 교육 목록 조회 응답(확장).
     * 카테고리/필수 여부/이수 상태/제목/설명을 제공합니다.
     */
    @Getter
    @AllArgsConstructor
    public static class EducationListItem {
        private UUID id;
        private String title;
        private String description;
        private EducationTopic category;
        private EducationCategory eduType;
        private boolean required;
        /** 사용자 기준 교육 진행률(%) */
        private int progressPercent;
        /** 교육 시청 상태(시청전/시청중/시청완료) */
        private String watchStatus;
        /** 교육에 포함된 영상 목록(사용자 진행 포함) */
        private List<EducationVideosResponse.VideoItem> videos;
    }

    /**
     * 영상 진행률 업데이트 응답 DTO.
     */
    @Getter
    @Builder
    public static class VideoProgressResponse {
        /** 진행 업데이트 처리 여부 */
        private boolean updated;
        /** 현재 영상 진행률(%) */
        private int progress;
        /** 현재 영상 이수 여부 */
        private boolean isCompleted;
        /** 사용자 누적 시청 시간(초) */
        private int totalWatchSeconds;
        /** 해당 교육 전체 진행률(%) */
        private int eduProgress;
        /** 해당 교육 모든 영상 이수 여부 */
        private boolean eduCompleted;
    }

    // ========================
    // 대시보드 통계 관련 DTOs
    // ========================

    /**
     * 대시보드 요약 통계 응답.
     */
    @Getter
    @AllArgsConstructor
    public static class DashboardSummaryResponse {
        /** 전체 평균 이수율(%) */
        private Double overallAverageCompletionRate;
        /** 미이수자 수 */
        private Long nonCompleterCount;
        /** 4대 의무교육 평균 이수율(%) */
        private Double mandatoryEducationAverage;
        /** 직무교육 평균 이수율(%) */
        private Double jobEducationAverage;
    }

    /**
     * 4대 의무교육 이수율 응답.
     */
    @Getter
    @AllArgsConstructor
    public static class MandatoryCompletionResponse {
        /** 성희롱 예방교육 이수율(%) */
        private Double sexualHarassmentPrevention;
        /** 개인정보보호 교육 이수율(%) */
        private Double personalInfoProtection;
        /** 직장 내 괴롭힘 예방 이수율(%) */
        private Double workplaceBullying;
        /** 장애인 인식개선 이수율(%) */
        private Double disabilityAwareness;
    }

    /**
     * 직무교육 이수 현황 항목.
     */
    @Getter
    @AllArgsConstructor
    public static class JobEducationCompletionItem {
        /** 교육 ID */
        private UUID educationId;
        /** 교육 제목 */
        private String title;
        /** 상태 (진행 중/이수 완료) */
        private String status;
        /** 학습자 수 */
        private Long learnerCount;
    }

    /**
     * 직무교육 이수 현황 응답.
     */
    @Getter
    @AllArgsConstructor
    public static class JobEducationCompletionResponse {
        private List<JobEducationCompletionItem> items;
    }

    /**
     * 부서별 이수율 현황 항목.
     */
    @Getter
    @AllArgsConstructor
    public static class DepartmentCompletionItem {
        /** 부서명 */
        private String department;
        /** 대상자 수 */
        private Long targetCount;
        /** 이수자 수 */
        private Long completerCount;
        /** 이수율(%) */
        private Double completionRate;
        /** 미이수자 수 */
        private Long nonCompleterCount;
    }

    /**
     * 부서별 이수율 현황 응답.
     */
    @Getter
    @AllArgsConstructor
    public static class DepartmentCompletionResponse {
        private List<DepartmentCompletionItem> items;
    }
}

