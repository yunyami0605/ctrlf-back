package com.ctrlf.infra.personalization.client;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Education Service 내부 API 호출 클라이언트.
 *
 * 퀴즈 응시 내역, 교육 진행 현황 등 개인화 데이터를 조회합니다.
 *
 * 엔드포인트:
 * - GET /internal/quiz/my-attempts (사용자 퀴즈 응시 내역)
 * - GET /internal/quiz/department-stats (부서별 통계)
 */
@Component
@Slf4j
public class EducationServiceClient {

    private final RestClient restClient;
    private final String baseUrl;

    public EducationServiceClient(
        @Value("${app.education-service.base-url:http://localhost:9002}") String baseUrl,
        @Value("${app.education-service.timeout-seconds:10}") long timeoutSeconds
    ) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;

        Duration timeout = Duration.ofSeconds(timeoutSeconds);
        org.springframework.http.client.SimpleClientHttpRequestFactory requestFactory =
            new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);

        this.restClient = RestClient.builder()
            .baseUrl(this.baseUrl)
            .requestFactory(requestFactory)
            .defaultRequest(request -> {
                request.headers(headers -> {
                    headers.setContentType(MediaType.APPLICATION_JSON);
                });
            })
            .build();
    }

    /**
     * 사용자의 모든 퀴즈 응시 내역을 조회합니다.
     *
     * @param userUuid 사용자 UUID
     * @return 퀴즈 응시 내역 목록
     */
    public List<MyAttemptItem> getMyAttempts(UUID userUuid) {
        try {
            return restClient.get()
                .uri("/internal/quiz/my-attempts")
                .header("X-User-Id", userUuid.toString())
                .retrieve()
                .body(new ParameterizedTypeReference<List<MyAttemptItem>>() {});
        } catch (RestClientException e) {
            log.warn("Failed to get my-attempts: userUuid={}, error={}", userUuid, e.getMessage());
            return List.of();
        }
    }

    /**
     * 부서별 퀴즈 통계를 조회합니다.
     *
     * @param educationId 교육 ID (null이면 전체)
     * @return 부서별 통계 목록
     */
    public List<DepartmentStatsItem> getDepartmentStats(UUID educationId) {
        try {
            String uri = educationId != null
                ? "/internal/quiz/department-stats?educationId=" + educationId
                : "/internal/quiz/department-stats";
            return restClient.get()
                .uri(uri)
                .retrieve()
                .body(new ParameterizedTypeReference<List<DepartmentStatsItem>>() {});
        } catch (RestClientException e) {
            log.warn("Failed to get department-stats: error={}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 사용자의 마지막 시청 영상 정보를 조회합니다 (Q4 이어보기용).
     *
     * @param userUuid 사용자 UUID
     * @return 마지막 시청 영상 정보 (없으면 null)
     */
    public LastVideoProgressItem getLastVideoProgress(UUID userUuid) {
        try {
            return restClient.get()
                .uri("/internal/video/last-progress")
                .header("X-User-Id", userUuid.toString())
                .retrieve()
                .body(LastVideoProgressItem.class);
        } catch (RestClientException e) {
            log.warn("Failed to get last-video-progress: userUuid={}, error={}", userUuid, e.getMessage());
            return null;
        }
    }

    // ---------- Response DTOs (education-service 응답과 일치) ----------

    @Getter
    @NoArgsConstructor
    public static class MyAttemptItem {
        private UUID attemptId;
        private UUID educationId;
        private String educationTitle;
        private Integer score;
        private Boolean passed;
        private Integer attemptNo;
        private String submittedAt;
        private Boolean isBestScore;
    }

    @Getter
    @NoArgsConstructor
    public static class DepartmentStatsItem {
        private String departmentName;
        private Integer averageScore;
        private Integer progressPercent;
        private Integer participantCount;
    }

    @Getter
    @NoArgsConstructor
    public static class LastVideoProgressItem {
        private String education_id;
        private String video_id;
        private String education_title;
        private String video_title;
        private Integer resume_position_seconds;
        private Integer progress_percent;
        private Integer duration;
    }

    // ---------- Q2, Q8용: 토픽별 교육 이수 현황 ----------

    /**
     * 사용자의 특정 토픽 교육 이수 현황을 조회합니다 (Q2, Q8용).
     *
     * @param userUuid 사용자 UUID
     * @param topic 교육 토픽 (WORKPLACE_BULLYING, SEXUAL_HARASSMENT_PREVENTION 등)
     * @return 토픽별 교육 이수 현황
     */
    public TopicProgressResponse getProgressByTopic(UUID userUuid, String topic) {
        try {
            return restClient.get()
                .uri("/internal/education/progress-by-topic?topic=" + topic)
                .header("X-User-Id", userUuid.toString())
                .retrieve()
                .body(TopicProgressResponse.class);
        } catch (RestClientException e) {
            log.warn("Failed to get progress-by-topic: userUuid={}, topic={}, error={}", userUuid, topic, e.getMessage());
            return null;
        }
    }

    @Getter
    @NoArgsConstructor
    public static class TopicProgressResponse {
        private String topic;
        private String topicLabel;
        private int educationCount;
        private int completedCount;
        private boolean isCompleted;
        private List<TopicEducationItem> items;
    }

    @Getter
    @NoArgsConstructor
    public static class TopicEducationItem {
        private String educationId;
        private String title;
        private boolean isCompleted;
        private String completedAt;
        private Integer progressPercent;
        private String deadline;
    }

    // ---------- Q19용: 토픽별 교육 마감일 ----------

    /**
     * 특정 토픽 교육의 마감일을 조회합니다 (Q19용).
     *
     * @param userUuid 사용자 UUID
     * @param topic 교육 토픽
     * @return 토픽별 마감일 정보
     */
    public TopicDeadlineResponse getDeadlineByTopic(UUID userUuid, String topic) {
        try {
            return restClient.get()
                .uri("/internal/education/deadline-by-topic?topic=" + topic)
                .header("X-User-Id", userUuid.toString())
                .retrieve()
                .body(TopicDeadlineResponse.class);
        } catch (RestClientException e) {
            log.warn("Failed to get deadline-by-topic: userUuid={}, topic={}, error={}", userUuid, topic, e.getMessage());
            return null;
        }
    }

    @Getter
    @NoArgsConstructor
    public static class TopicDeadlineResponse {
        private String topic;
        private String topicLabel;
        private boolean hasDeadline;
        private String nearestDeadline;
        private List<TopicDeadlineItem> items;
    }

    @Getter
    @NoArgsConstructor
    public static class TopicDeadlineItem {
        private String educationId;
        private String title;
        private String deadline;
        private Boolean isCompleted;
    }

    // ---------- Q7, Q18용: 토픽별 퀴즈 점수 ----------

    /**
     * 사용자의 특정 토픽 퀴즈 점수를 조회합니다 (Q7, Q18용).
     *
     * @param userUuid 사용자 UUID
     * @param topic 교육 토픽
     * @return 토픽별 퀴즈 점수 정보
     */
    public TopicScoreResponse getScoreByTopic(UUID userUuid, String topic) {
        try {
            return restClient.get()
                .uri("/internal/quiz/score-by-topic?topic=" + topic)
                .header("X-User-Id", userUuid.toString())
                .retrieve()
                .body(TopicScoreResponse.class);
        } catch (RestClientException e) {
            log.warn("Failed to get score-by-topic: userUuid={}, topic={}, error={}", userUuid, topic, e.getMessage());
            return null;
        }
    }

    @Getter
    @NoArgsConstructor
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
    @NoArgsConstructor
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
