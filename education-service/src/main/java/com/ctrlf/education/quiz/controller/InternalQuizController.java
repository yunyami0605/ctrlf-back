package com.ctrlf.education.quiz.controller;

import com.ctrlf.education.entity.Education;
import com.ctrlf.education.entity.EducationTopic;
import com.ctrlf.education.quiz.entity.QuizAttempt;
import com.ctrlf.education.quiz.repository.QuizAttemptRepository;
import com.ctrlf.education.repository.EducationRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
 * 퀴즈 내부 API 컨트롤러 (infra-service ↔ education-service).
 * 개인화 서비스(Q7, Q18)를 위한 내부 API를 제공합니다.
 */
@Tag(name = "Internal - Quiz", description = "퀴즈 내부 API (Personalization용)")
@RestController
@RequestMapping("/internal/quiz")
@SecurityRequirement(name = "internal-token")
@RequiredArgsConstructor
@Slf4j
public class InternalQuizController {

    private final QuizAttemptRepository quizAttemptRepository;
    private final EducationRepository educationRepository;

    /**
     * 사용자의 특정 토픽 퀴즈 점수를 조회합니다 (Q7, Q18용).
     *
     * @param userId 사용자 UUID (X-User-Id 헤더)
     * @param topic 교육 토픽 (WORKPLACE_BULLYING, SEXUAL_HARASSMENT_PREVENTION 등)
     * @return 해당 토픽 퀴즈 점수 정보
     */
    @Operation(summary = "토픽별 퀴즈 점수 조회 (Q7, Q18)",
        description = "사용자의 특정 토픽 퀴즈 점수를 조회합니다. 교육별 최고 점수를 반환합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(schema = @Schema(implementation = TopicScoreResponse.class))),
        @ApiResponse(responseCode = "400", description = "잘못된 요청"),
        @ApiResponse(responseCode = "401", description = "내부 토큰 오류")
    })
    @GetMapping("/score-by-topic")
    public ResponseEntity<TopicScoreResponse> getScoreByTopic(
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

        log.info("getScoreByTopic: userUuid={}, topic={}", userUuid, educationTopic);

        // 해당 토픽의 교육 목록 조회
        List<Education> topicEducations = educationRepository.findAll().stream()
            .filter(e -> e.getDeletedAt() == null)
            .filter(e -> e.getCategory() == educationTopic)
            .collect(Collectors.toList());

        if (topicEducations.isEmpty()) {
            return ResponseEntity.ok(TopicScoreResponse.builder()
                .topic(topic)
                .topicLabel(getTopicLabel(educationTopic))
                .educationCount(0)
                .attemptedCount(0)
                .hasAttempt(false)
                .build());
        }

        List<UUID> topicEducationIds = topicEducations.stream()
            .map(Education::getId)
            .collect(Collectors.toList());

        // 사용자의 퀴즈 응시 내역 조회
        List<QuizAttempt> allAttempts = quizAttemptRepository
            .findByUserUuidAndSubmittedAtIsNotNullOrderByCreatedAtDesc(userUuid);

        // 해당 토픽 교육의 퀴즈만 필터링
        List<QuizAttempt> topicAttempts = allAttempts.stream()
            .filter(a -> topicEducationIds.contains(a.getEducationId()))
            .collect(Collectors.toList());

        if (topicAttempts.isEmpty()) {
            return ResponseEntity.ok(TopicScoreResponse.builder()
                .topic(topic)
                .topicLabel(getTopicLabel(educationTopic))
                .educationCount(topicEducations.size())
                .attemptedCount(0)
                .hasAttempt(false)
                .build());
        }

        // 교육별로 그룹화하여 최고 점수 추출
        Map<UUID, List<QuizAttempt>> attemptsByEducation = topicAttempts.stream()
            .collect(Collectors.groupingBy(QuizAttempt::getEducationId));

        List<TopicScoreItem> items = topicEducations.stream()
            .map(edu -> {
                List<QuizAttempt> eduAttempts = attemptsByEducation.get(edu.getId());

                if (eduAttempts == null || eduAttempts.isEmpty()) {
                    return TopicScoreItem.builder()
                        .educationId(edu.getId().toString())
                        .title(edu.getTitle())
                        .hasAttempt(false)
                        .passScore(edu.getPassScore())
                        .build();
                }

                // 최고 점수 찾기
                QuizAttempt bestAttempt = eduAttempts.stream()
                    .filter(a -> a.getScore() != null)
                    .max(Comparator.comparingInt(QuizAttempt::getScore))
                    .orElse(null);

                if (bestAttempt == null) {
                    return TopicScoreItem.builder()
                        .educationId(edu.getId().toString())
                        .title(edu.getTitle())
                        .hasAttempt(true)
                        .attemptCount(eduAttempts.size())
                        .passScore(edu.getPassScore())
                        .build();
                }

                boolean passed = edu.getPassScore() != null && bestAttempt.getScore() >= edu.getPassScore();

                return TopicScoreItem.builder()
                    .educationId(edu.getId().toString())
                    .title(edu.getTitle())
                    .hasAttempt(true)
                    .bestScore(bestAttempt.getScore())
                    .passed(passed)
                    .attemptCount(eduAttempts.size())
                    .passScore(edu.getPassScore())
                    .lastAttemptAt(bestAttempt.getSubmittedAt() != null ? bestAttempt.getSubmittedAt().toString() : null)
                    .build();
            })
            .collect(Collectors.toList());

        // 통계 계산
        int attemptedCount = (int) items.stream().filter(TopicScoreItem::isHasAttempt).count();
        int passedCount = (int) items.stream().filter(i -> Boolean.TRUE.equals(i.getPassed())).count();

        // 평균 점수 계산 (응시한 교육만)
        double averageScore = items.stream()
            .filter(i -> i.getBestScore() != null)
            .mapToInt(TopicScoreItem::getBestScore)
            .average()
            .orElse(0.0);

        return ResponseEntity.ok(TopicScoreResponse.builder()
            .topic(topic)
            .topicLabel(getTopicLabel(educationTopic))
            .educationCount(topicEducations.size())
            .attemptedCount(attemptedCount)
            .passedCount(passedCount)
            .hasAttempt(attemptedCount > 0)
            .averageScore(Math.round(averageScore * 10) / 10.0)
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
    public static class TopicScoreResponse {
        private String topic;
        private String topicLabel;
        private int educationCount;
        private int attemptedCount;
        private int passedCount;
        private boolean hasAttempt;
        private Double averageScore;
        private List<TopicScoreItem> items;
    }

    @Getter
    @Builder
    public static class TopicScoreItem {
        private String educationId;
        private String title;
        private boolean hasAttempt;
        private Integer bestScore;
        private Boolean passed;
        private Integer attemptCount;
        private Integer passScore;
        private String lastAttemptAt;
    }
}
