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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
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
        List<Education> topicEducations = educationRepository.findByTopic(educationTopic);

        if (topicEducations.isEmpty()) {
            return ResponseEntity.ok(TopicProgressResponse.builder()
                .topic(topic)
                .educationCount(0)
                .completedCount(0)
                .isCompleted(false)
                .build());
        }

        // 교육 ID 목록 추출
        Set<UUID> educationIds = topicEducations.stream()
            .map(Education::getId)
            .collect(Collectors.toSet());

        // 사용자의 해당 교육들에 대한 진행 정보를 한 번에 조회
        List<EducationProgress> progresses = educationProgressRepository
            .findByUserUuidAndEducationIdIn(userUuid, educationIds);
        Map<UUID, EducationProgress> progressMap = progresses.stream()
            .collect(Collectors.toMap(EducationProgress::getEducationId, Function.identity()));

        // 해당 토픽에서 완료한 교육 수
        long completedCount = progresses.stream()
            .filter(p -> Boolean.TRUE.equals(p.getIsCompleted()))
            .count();

        // 교육별 상세 정보 (캐시된 데이터 사용)
        List<TopicEducationItem> items = topicEducations.stream()
            .map(edu -> {
                EducationProgress progress = progressMap.get(edu.getId());

                boolean completed = progress != null && Boolean.TRUE.equals(progress.getIsCompleted());
                Instant completedAt = progress != null ? progress.getCompletedAt() : null;
                Integer progressPercent = progress != null ? progress.getProgress() : 0;

                return TopicEducationItem.builder()
                    .educationId(edu.getId().toString())
                    .title(edu.getTitle())
                    .isCompleted(completed)
                    .completedAt(completedAt != null ? completedAt.toString() : null)
                    .progressPercent(progressPercent != null ? progressPercent : 0)
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

        // 해당 토픽의 마감일 있는 교육 목록 조회
        List<Education> topicEducations = educationRepository.findByTopicWithDeadline(educationTopic);

        if (topicEducations.isEmpty()) {
            return ResponseEntity.ok(TopicDeadlineResponse.builder()
                .topic(topic)
                .topicLabel(getTopicLabel(educationTopic))
                .hasDeadline(false)
                .build());
        }

        // 교육 ID 목록 추출
        Set<UUID> educationIds = topicEducations.stream()
            .map(Education::getId)
            .collect(Collectors.toSet());

        // 사용자의 해당 교육들에 대한 완료 정보를 한 번에 조회
        List<EducationProgress> progresses = educationProgressRepository
            .findByUserUuidAndEducationIdIn(userUuid, educationIds);
        Set<UUID> completedEducationIds = progresses.stream()
            .filter(p -> Boolean.TRUE.equals(p.getIsCompleted()))
            .map(EducationProgress::getEducationId)
            .collect(Collectors.toSet());

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
     * 사용자의 미이수 필수 교육 목록을 조회합니다 (Q1용).
     *
     * @param userId 사용자 UUID (X-User-Id 헤더)
     * @return 미이수 필수 교육 목록
     */
    @Operation(summary = "미이수 필수 교육 조회 (Q1)",
        description = "사용자의 미이수 필수 교육 목록을 조회합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(schema = @Schema(implementation = IncompleteMandatoryResponse.class))),
        @ApiResponse(responseCode = "401", description = "내부 토큰 오류")
    })
    @GetMapping("/incomplete-mandatory")
    public ResponseEntity<IncompleteMandatoryResponse> getIncompleteMandatory(
        @Parameter(description = "사용자 UUID", required = true)
        @RequestHeader("X-User-Id") String userId
    ) {
        UUID userUuid = UUID.fromString(userId);
        log.info("getIncompleteMandatory: userUuid={}", userUuid);

        Instant now = Instant.now();

        // 필수 활성 교육 목록 조회
        List<Education> mandatoryEducations = educationRepository.findActiveMandatory(now);

        if (mandatoryEducations.isEmpty()) {
            return ResponseEntity.ok(IncompleteMandatoryResponse.builder()
                .totalRequired(0)
                .completed(0)
                .remaining(0)
                .items(List.of())
                .build());
        }

        // 교육 ID 목록 추출
        Set<UUID> educationIds = mandatoryEducations.stream()
            .map(Education::getId)
            .collect(Collectors.toSet());

        // 사용자의 해당 교육들에 대한 완료 정보를 한 번에 조회
        List<EducationProgress> progresses = educationProgressRepository
            .findByUserUuidAndEducationIdIn(userUuid, educationIds);
        Set<UUID> completedEducationIds = progresses.stream()
            .filter(p -> Boolean.TRUE.equals(p.getIsCompleted()))
            .map(EducationProgress::getEducationId)
            .collect(Collectors.toSet());

        // 미이수 필수 교육 필터링
        List<IncompleteMandatoryItem> items = mandatoryEducations.stream()
            .filter(e -> !completedEducationIds.contains(e.getId()))
            .map(edu -> IncompleteMandatoryItem.builder()
                .educationId(edu.getId().toString())
                .title(edu.getTitle())
                .deadline(edu.getEndAt() != null ? edu.getEndAt().toString().substring(0, 10) : null)
                .status("미이수")
                .build())
            .collect(Collectors.toList());

        int totalRequired = mandatoryEducations.size();
        int completed = totalRequired - items.size();

        return ResponseEntity.ok(IncompleteMandatoryResponse.builder()
            .totalRequired(totalRequired)
            .completed(completed)
            .remaining(items.size())
            .items(items)
            .build());
    }

    /**
     * 이번 달 마감인 필수 교육 목록을 조회합니다 (Q3용).
     *
     * @param userId 사용자 UUID (X-User-Id 헤더)
     * @return 이번 달 마감 필수 교육 목록
     */
    @Operation(summary = "이번 달 마감 필수 교육 조회 (Q3)",
        description = "이번 달 마감인 필수 교육 목록을 조회합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(schema = @Schema(implementation = DeadlinesThisMonthResponse.class))),
        @ApiResponse(responseCode = "401", description = "내부 토큰 오류")
    })
    @GetMapping("/deadlines-this-month")
    public ResponseEntity<DeadlinesThisMonthResponse> getDeadlinesThisMonth(
        @Parameter(description = "사용자 UUID", required = true)
        @RequestHeader("X-User-Id") String userId
    ) {
        UUID userUuid = UUID.fromString(userId);
        log.info("getDeadlinesThisMonth: userUuid={}", userUuid);

        LocalDate today = LocalDate.now();
        LocalDate firstDayOfMonth = today.withDayOfMonth(1);
        LocalDate lastDayOfMonth = today.with(TemporalAdjusters.lastDayOfMonth());

        Instant monthStart = firstDayOfMonth.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant monthEnd = lastDayOfMonth.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        // 이번 달 마감인 필수 교육 목록 조회
        List<Education> deadlineEducations = educationRepository.findMandatoryWithDeadlineBetween(monthStart, monthEnd);

        if (deadlineEducations.isEmpty()) {
            return ResponseEntity.ok(DeadlinesThisMonthResponse.builder()
                .deadlineCount(0)
                .items(List.of())
                .build());
        }

        // 교육 ID 목록 추출
        Set<UUID> educationIds = deadlineEducations.stream()
            .map(Education::getId)
            .collect(Collectors.toSet());

        // 사용자의 해당 교육들에 대한 완료 정보를 한 번에 조회
        List<EducationProgress> progresses = educationProgressRepository
            .findByUserUuidAndEducationIdIn(userUuid, educationIds);
        Set<UUID> completedEducationIds = progresses.stream()
            .filter(p -> Boolean.TRUE.equals(p.getIsCompleted()))
            .map(EducationProgress::getEducationId)
            .collect(Collectors.toSet());

        // 미완료 교육만 필터링하고 마감일 기준 정렬
        List<DeadlineEducationItem> items = deadlineEducations.stream()
            .filter(e -> !completedEducationIds.contains(e.getId()))
            .map(edu -> {
                long daysLeft = java.time.Duration.between(Instant.now(), edu.getEndAt()).toDays();
                return DeadlineEducationItem.builder()
                    .educationId(edu.getId().toString())
                    .title(edu.getTitle())
                    .deadline(edu.getEndAt().toString().substring(0, 10))
                    .daysLeft((int) Math.max(0, daysLeft))
                    .build();
            })
            .sorted((a, b) -> Integer.compare(a.getDaysLeft(), b.getDaysLeft()))
            .collect(Collectors.toList());

        return ResponseEntity.ok(DeadlinesThisMonthResponse.builder()
            .deadlineCount(items.size())
            .items(items)
            .build());
    }

    /**
     * 이번 주 할 일(교육/퀴즈) 목록을 조회합니다 (Q9용).
     *
     * @param userId 사용자 UUID (X-User-Id 헤더)
     * @return 이번 주 할 일 목록
     */
    @Operation(summary = "이번 주 할 일 조회 (Q9)",
        description = "이번 주 마감인 교육/퀴즈 목록을 조회합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(schema = @Schema(implementation = TodosThisWeekResponse.class))),
        @ApiResponse(responseCode = "401", description = "내부 토큰 오류")
    })
    @GetMapping("/todos-this-week")
    public ResponseEntity<TodosThisWeekResponse> getTodosThisWeek(
        @Parameter(description = "사용자 UUID", required = true)
        @RequestHeader("X-User-Id") String userId
    ) {
        UUID userUuid = UUID.fromString(userId);
        log.info("getTodosThisWeek: userUuid={}", userUuid);

        LocalDate today = LocalDate.now();
        // 이번 주 일요일까지 (또는 다음 7일)
        LocalDate weekEnd = today.plusDays(7);

        Instant now = Instant.now();
        Instant weekEndInstant = weekEnd.atStartOfDay(ZoneId.systemDefault()).toInstant();

        // 이번 주 마감인 교육 목록 조회
        List<Education> weekEducations = educationRepository.findWithDeadlineBetween(now, weekEndInstant);

        if (weekEducations.isEmpty()) {
            return ResponseEntity.ok(TodosThisWeekResponse.builder()
                .todoCount(0)
                .items(List.of())
                .build());
        }

        // 교육 ID 목록 추출
        Set<UUID> educationIds = weekEducations.stream()
            .map(Education::getId)
            .collect(Collectors.toSet());

        // 사용자의 해당 교육들에 대한 완료 정보를 한 번에 조회
        List<EducationProgress> progresses = educationProgressRepository
            .findByUserUuidAndEducationIdIn(userUuid, educationIds);
        Set<UUID> completedEducationIds = progresses.stream()
            .filter(p -> Boolean.TRUE.equals(p.getIsCompleted()))
            .map(EducationProgress::getEducationId)
            .collect(Collectors.toSet());

        // 미완료 교육만 필터링
        List<TodoItem> items = new ArrayList<>();

        for (Education edu : weekEducations) {
            if (!completedEducationIds.contains(edu.getId())) {
                // 교육 시청 할 일
                items.add(TodoItem.builder()
                    .type("education")
                    .title(edu.getTitle())
                    .deadline(edu.getEndAt().toString().substring(0, 10))
                    .build());

                // 퀴즈 할 일 (퀴즈가 있는 교육인 경우 - passScore가 설정된 경우)
                if (edu.getPassScore() != null && edu.getPassScore() > 0) {
                    items.add(TodoItem.builder()
                        .type("quiz")
                        .title(edu.getTitle() + " 퀴즈")
                        .deadline(edu.getEndAt().toString().substring(0, 10))
                        .build());
                }
            }
        }

        // 마감일 기준 정렬
        items.sort((a, b) -> a.getDeadline().compareTo(b.getDeadline()));

        return ResponseEntity.ok(TodosThisWeekResponse.builder()
            .todoCount(items.size())
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

    // ---------- Q1: 미이수 필수 교육 ----------

    @Getter
    @Builder
    public static class IncompleteMandatoryResponse {
        private int totalRequired;
        private int completed;
        private int remaining;
        private List<IncompleteMandatoryItem> items;
    }

    @Getter
    @Builder
    public static class IncompleteMandatoryItem {
        private String educationId;
        private String title;
        private String deadline;
        private String status;
    }

    // ---------- Q3: 이번 달 마감 필수 교육 ----------

    @Getter
    @Builder
    public static class DeadlinesThisMonthResponse {
        private int deadlineCount;
        private List<DeadlineEducationItem> items;
    }

    @Getter
    @Builder
    public static class DeadlineEducationItem {
        private String educationId;
        private String title;
        private String deadline;
        private int daysLeft;
    }

    // ---------- Q9: 이번 주 할 일 ----------

    @Getter
    @Builder
    public static class TodosThisWeekResponse {
        private int todoCount;
        private List<TodoItem> items;
    }

    @Getter
    @Builder
    public static class TodoItem {
        private String type;  // "education" | "quiz"
        private String title;
        private String deadline;
    }
}
