package com.ctrlf.education.controller;

import com.ctrlf.education.entity.Education;
import com.ctrlf.education.entity.EducationProgress;
import com.ctrlf.education.entity.EducationTopic;
import com.ctrlf.education.repository.EducationProgressRepository;
import com.ctrlf.education.repository.EducationRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 교육 내부 API 컨트롤러 (infra-service ↔ education-service).
 * 개인화 서비스(Q2, Q8, Q19)를 위한 내부 API를 제공합니다.
 */
@Tag(name = "Internal - Education", description = "교육 내부 API (Personalization용)")
@RestController
@RequestMapping("/internal/education")
@SecurityRequirement(name = "internal-token")
@RequiredArgsConstructor
@Slf4j
public class InternalEducationController {

    private final EducationRepository educationRepository;
    private final EducationProgressRepository educationProgressRepository;

    /**
     * 사용자의 특정 토픽 교육 이수 현황을 조회합니다 (Q2, Q8용).
     *
     * @param userId 사용자 UUID (X-User-Id 헤더)
     * @param topic 교육 토픽 (WORKPLACE_BULLYING, SEXUAL_HARASSMENT_PREVENTION 등)
     * @return 해당 토픽 교육의 이수 현황
     */
    @Operation(summary = "토픽별 교육 이수 현황 조회 (Q2, Q8)",
        description = "사용자의 특정 토픽 교육 이수 여부를 조회합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(schema = @Schema(implementation = TopicProgressResponse.class))),
        @ApiResponse(responseCode = "400", description = "잘못된 요청"),
        @ApiResponse(responseCode = "401", description = "내부 토큰 오류")
    })
    @GetMapping("/progress-by-topic")
    public ResponseEntity<TopicProgressResponse> getProgressByTopic(
        @Parameter(description = "사용자 UUID", required = true)
        @RequestHeader("X-User-Id") String userId,
        @Parameter(description = "교육 토픽", required = true)
        @RequestParam("topic") String topic
    ) {
        UUID userUuid = UUID.fromString(userId);
        EducationTopic educationTopic;

        try {
            educationTopic = EducationTopic.valueOf(topic.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid topic: {}", topic);
            return ResponseEntity.badRequest().build();
        }

        log.info("getProgressByTopic: userUuid={}, topic={}", userUuid, educationTopic);

        // 해당 토픽의 교육 목록 조회
        List<Education> topicEducations = educationRepository.findAll().stream()
            .filter(e -> e.getDeletedAt() == null)
            .filter(e -> e.getCategory() == educationTopic)
            .collect(Collectors.toList());

        if (topicEducations.isEmpty()) {
            return ResponseEntity.ok(TopicProgressResponse.builder()
                .topic(topic)
                .educationCount(0)
                .completedCount(0)
                .isCompleted(false)
                .build());
        }

        // 사용자의 완료된 교육 조회
        List<EducationProgress> completedProgresses = educationProgressRepository
            .findByUserUuidAndIsCompletedTrue(userUuid);

        List<UUID> completedEducationIds = completedProgresses.stream()
            .map(EducationProgress::getEducationId)
            .collect(Collectors.toList());

        // 해당 토픽에서 완료한 교육 수
        long completedCount = topicEducations.stream()
            .filter(e -> completedEducationIds.contains(e.getId()))
            .count();

        // 교육별 상세 정보
        List<TopicEducationItem> items = topicEducations.stream()
            .map(edu -> {
                Optional<EducationProgress> progress = educationProgressRepository
                    .findByUserUuidAndEducationId(userUuid, edu.getId());

                boolean completed = progress.map(p -> Boolean.TRUE.equals(p.getIsCompleted())).orElse(false);
                Instant completedAt = progress.map(EducationProgress::getCompletedAt).orElse(null);
                Integer progressPercent = progress.map(EducationProgress::getProgress).orElse(0);

                return TopicEducationItem.builder()
                    .educationId(edu.getId().toString())
                    .title(edu.getTitle())
                    .isCompleted(completed)
                    .completedAt(completedAt != null ? completedAt.toString() : null)
                    .progressPercent(progressPercent)
                    .deadline(edu.getEndAt() != null ? edu.getEndAt().toString() : null)
                    .build();
            })
            .collect(Collectors.toList());

        return ResponseEntity.ok(TopicProgressResponse.builder()
            .topic(topic)
            .topicLabel(getTopicLabel(educationTopic))
            .educationCount(topicEducations.size())
            .completedCount((int) completedCount)
            .isCompleted(completedCount == topicEducations.size() && !topicEducations.isEmpty())
            .items(items)
            .build());
    }

    /**
     * 사용자의 특정 토픽 교육 마감일을 조회합니다 (Q19용).
     *
     * @param userId 사용자 UUID (X-User-Id 헤더)
     * @param topic 교육 토픽
     * @return 해당 토픽 교육의 마감일 정보
     */
    @Operation(summary = "토픽별 교육 마감일 조회 (Q19)",
        description = "특정 토픽 교육의 마감일을 조회합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(schema = @Schema(implementation = TopicDeadlineResponse.class))),
        @ApiResponse(responseCode = "400", description = "잘못된 요청"),
        @ApiResponse(responseCode = "401", description = "내부 토큰 오류")
    })
    @GetMapping("/deadline-by-topic")
    public ResponseEntity<TopicDeadlineResponse> getDeadlineByTopic(
        @Parameter(description = "사용자 UUID", required = true)
        @RequestHeader("X-User-Id") String userId,
        @Parameter(description = "교육 토픽", required = true)
        @RequestParam("topic") String topic
    ) {
        UUID userUuid = UUID.fromString(userId);
        EducationTopic educationTopic;

        try {
            educationTopic = EducationTopic.valueOf(topic.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid topic: {}", topic);
            return ResponseEntity.badRequest().build();
        }

        log.info("getDeadlineByTopic: userUuid={}, topic={}", userUuid, educationTopic);

        // 해당 토픽의 교육 목록 조회 (마감일이 있는 것만)
        List<Education> topicEducations = educationRepository.findAll().stream()
            .filter(e -> e.getDeletedAt() == null)
            .filter(e -> e.getCategory() == educationTopic)
            .filter(e -> e.getEndAt() != null)
            .collect(Collectors.toList());

        if (topicEducations.isEmpty()) {
            return ResponseEntity.ok(TopicDeadlineResponse.builder()
                .topic(topic)
                .topicLabel(getTopicLabel(educationTopic))
                .hasDeadline(false)
                .build());
        }

        // 사용자의 완료된 교육 조회
        List<EducationProgress> completedProgresses = educationProgressRepository
            .findByUserUuidAndIsCompletedTrue(userUuid);
        List<UUID> completedEducationIds = completedProgresses.stream()
            .map(EducationProgress::getEducationId)
            .collect(Collectors.toList());

        // 미완료 교육 중 가장 가까운 마감일 찾기
        List<TopicDeadlineItem> items = topicEducations.stream()
            .map(edu -> {
                boolean completed = completedEducationIds.contains(edu.getId());
                return TopicDeadlineItem.builder()
                    .educationId(edu.getId().toString())
                    .title(edu.getTitle())
                    .deadline(edu.getEndAt().toString())
                    .isCompleted(completed)
                    .build();
            })
            .sorted((a, b) -> a.getDeadline().compareTo(b.getDeadline()))
            .collect(Collectors.toList());

        // 미완료 중 가장 가까운 마감일
        String nearestDeadline = items.stream()
            .filter(item -> !item.getIsCompleted())
            .map(TopicDeadlineItem::getDeadline)
            .findFirst()
            .orElse(null);

        return ResponseEntity.ok(TopicDeadlineResponse.builder()
            .topic(topic)
            .topicLabel(getTopicLabel(educationTopic))
            .hasDeadline(true)
            .nearestDeadline(nearestDeadline)
            .items(items)
            .build());
    }

    /**
     * 토픽에 대한 한글 라벨 반환.
     */
    private String getTopicLabel(EducationTopic topic) {
        return switch (topic) {
            case WORKPLACE_BULLYING -> "직장 내 괴롭힘 예방";
            case SEXUAL_HARASSMENT_PREVENTION -> "성희롱 예방";
            case PERSONAL_INFO_PROTECTION -> "개인정보 보호";
            case DISABILITY_AWARENESS -> "장애인 인식 개선";
            case JOB_DUTY -> "직무 교육";
        };
    }

    // ---------- Response DTOs ----------

    @Getter
    @Builder
    public static class TopicProgressResponse {
        private String topic;
        private String topicLabel;
        private int educationCount;
        private int completedCount;
        private boolean isCompleted;
        private List<TopicEducationItem> items;
    }

    @Getter
    @Builder
    public static class TopicEducationItem {
        private String educationId;
        private String title;
        private boolean isCompleted;
        private String completedAt;
        private Integer progressPercent;
        private String deadline;
    }

    @Getter
    @Builder
    public static class TopicDeadlineResponse {
        private String topic;
        private String topicLabel;
        private boolean hasDeadline;
        private String nearestDeadline;
        private List<TopicDeadlineItem> items;
    }

    @Getter
    @Builder
    public static class TopicDeadlineItem {
        private String educationId;
        private String title;
        private String deadline;
        private Boolean isCompleted;
    }
}
