package com.ctrlf.infra.personalization.service;

import static com.ctrlf.infra.personalization.dto.PersonalizationDtos.*;

import com.ctrlf.infra.personalization.client.EducationServiceClient;
import com.ctrlf.infra.personalization.client.EducationServiceClient.DepartmentStatsItem;
import com.ctrlf.infra.personalization.client.EducationServiceClient.LastVideoProgressItem;
import com.ctrlf.infra.personalization.client.EducationServiceClient.MyAttemptItem;
import com.ctrlf.infra.personalization.client.EducationServiceClient.TopicProgressResponse;
import com.ctrlf.infra.personalization.client.EducationServiceClient.TopicEducationItem;
import com.ctrlf.infra.personalization.client.EducationServiceClient.TopicScoreResponse;
import com.ctrlf.infra.personalization.client.EducationServiceClient.TopicScoreItem;
import com.ctrlf.infra.personalization.client.EducationServiceClient.TopicDeadlineResponse;
import com.ctrlf.infra.personalization.client.EducationServiceClient.TopicDeadlineItem;
import com.ctrlf.infra.personalization.dto.PersonalizationDtos;
import com.ctrlf.infra.personalization.util.PeriodCalculator;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Personalization 비즈니스 로직 서비스.
 * AI Gateway에서 개인화 데이터를 요청하는 API를 처리합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PersonalizationService {

    private static final DateTimeFormatter ISO_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneId.systemDefault());

    private final EducationServiceClient educationServiceClient;

    /**
     * 개인화 facts를 조회합니다.
     * 
     * @param userId 사용자 ID (X-User-Id 헤더에서 추출)
     * @param request 요청 DTO
     * @return 개인화 facts 응답
     */
    @Transactional(readOnly = true)
    public ResolveResponse resolve(String userId, ResolveRequest request) {
        String subIntentId = request.getSub_intent_id();
        String period = request.getPeriod();
        String targetDeptId = request.getTarget_dept_id();

        log.info("Personalization resolve request: userId={}, subIntentId={}, period={}, targetDeptId={}", 
            userId, subIntentId, period, targetDeptId);

        // Period 계산
        PeriodCalculator.PeriodDates periodDates = PeriodCalculator.calculatePeriod(period);
        String periodStart = periodDates.getStart();
        String periodEnd = periodDates.getEnd();
        String updatedAt = ISO_FORMATTER.format(Instant.now());

        try {
            // 인텐트별 핸들러 호출
            ResolveResponse response = switch (subIntentId) {
                case "Q1" -> handleQ1(userId, periodStart, periodEnd, updatedAt);
                case "Q2" -> handleQ2(userId, periodStart, periodEnd, updatedAt, request.getTopic());
                case "Q3" -> handleQ3(userId, periodStart, periodEnd, updatedAt);
                case "Q4" -> handleQ4(userId, periodStart, periodEnd, updatedAt);
                case "Q5" -> handleQ5(userId, periodStart, periodEnd, updatedAt, targetDeptId);
                case "Q6" -> handleQ6(userId, periodStart, periodEnd, updatedAt);
                case "Q7" -> handleQ7(userId, periodStart, periodEnd, updatedAt, request.getTopic());
                case "Q8" -> handleQ8(userId, periodStart, periodEnd, updatedAt, request.getTopic());
                case "Q9" -> handleQ9(userId, periodStart, periodEnd, updatedAt);
                case "Q11" -> handleQ11(userId, periodStart, periodEnd, updatedAt);
                case "Q14" -> handleQ14(userId, periodStart, periodEnd, updatedAt);
                case "Q18" -> handleQ18(userId, periodStart, periodEnd, updatedAt, request.getTopic());
                case "Q19" -> handleQ19(userId, periodStart, periodEnd, updatedAt, request.getTopic());
                case "Q20" -> handleQ20(userId, periodStart, periodEnd, updatedAt);
                default -> createErrorResponse(subIntentId, periodStart, periodEnd, updatedAt,
                    "NOT_IMPLEMENTED", "현재 데모 범위에서는 지원하지 않는 질문이에요.");
            };
            return response;
        } catch (Exception e) {
            log.error("Error resolving personalization data: userId={}, subIntentId={}", userId, subIntentId, e);
            return createErrorResponse(subIntentId, periodStart, periodEnd, updatedAt, 
                "TIMEOUT", "지금 조회가 지연되고 있어요.");
        }
    }

    // ---------- Q1: 미이수 필수 교육 조회 ----------
    private ResolveResponse handleQ1(String userId, String periodStart, String periodEnd, String updatedAt) {
        // TODO: education-service에서 필수 교육 및 완료 여부 조회
        // 현재는 스텁 데이터 반환
        log.warn("Q1 handler: Stub implementation - userId={}", userId);
        
        Q1Metrics metrics = new Q1Metrics(5, 3, 2);
        List<Object> items = new ArrayList<>();
        items.add(new Q1EducationItem("EDU001", "개인정보보호 교육", "2025-01-31", "미이수"));
        items.add(new Q1EducationItem("EDU002", "정보보안 교육", "2025-02-15", "미이수"));

        return new ResolveResponse(
            "Q1", periodStart, periodEnd, updatedAt,
            Map.of(
                "total_required", metrics.getTotal_required(),
                "completed", metrics.getCompleted(),
                "remaining", metrics.getRemaining()
            ),
            items,
            Map.of(),
            null
        );
    }

    // ---------- Q2: 특정 토픽 교육 이수 여부 ----------
    private ResolveResponse handleQ2(String userId, String periodStart, String periodEnd, String updatedAt, String topic) {
        log.info("Q2 handler: userId={}, topic={}", userId, topic);

        if (topic == null || topic.isBlank()) {
            return createErrorResponse("Q2", periodStart, periodEnd, updatedAt,
                "MISSING_TOPIC", "조회할 교육 토픽을 지정해주세요.");
        }

        try {
            UUID userUuid = UUID.fromString(userId);
            TopicProgressResponse progress = educationServiceClient.getProgressByTopic(userUuid, topic);

            if (progress == null) {
                return createErrorResponse("Q2", periodStart, periodEnd, updatedAt,
                    "SERVICE_ERROR", "교육 정보를 조회할 수 없어요.");
            }

            // 교육 목록을 items로 변환
            List<Object> items = new ArrayList<>();
            if (progress.getItems() != null) {
                for (TopicEducationItem item : progress.getItems()) {
                    items.add(Map.of(
                        "education_id", item.getEducationId(),
                        "title", item.getTitle(),
                        "is_completed", item.isCompleted(),
                        "completed_at", item.getCompletedAt() != null ? item.getCompletedAt() : "",
                        "progress_percent", item.getProgressPercent() != null ? item.getProgressPercent() : 0
                    ));
                }
            }

            return new ResolveResponse(
                "Q2", periodStart, periodEnd, updatedAt,
                Map.of(
                    "topic", topic,
                    "topic_label", progress.getTopicLabel() != null ? progress.getTopicLabel() : topic,
                    "education_count", progress.getEducationCount(),
                    "completed_count", progress.getCompletedCount(),
                    "is_completed", progress.isCompleted()
                ),
                items,
                Map.of(),
                null
            );
        } catch (IllegalArgumentException e) {
            log.warn("Invalid userId format: {}", userId);
            return createErrorResponse("Q2", periodStart, periodEnd, updatedAt,
                "INVALID_USER", "사용자 정보를 확인할 수 없어요.");
        } catch (Exception e) {
            log.error("Error in Q2 handler: userId={}, topic={}", userId, topic, e);
            return createErrorResponse("Q2", periodStart, periodEnd, updatedAt,
                "SERVICE_ERROR", "데이터를 조회하는 중 오류가 발생했어요.");
        }
    }

    // ---------- Q3: 이번 달 데드라인 필수 교육 ----------
    private ResolveResponse handleQ3(String userId, String periodStart, String periodEnd, String updatedAt) {
        // TODO: education-service에서 이번 달 마감 필수 교육 조회
        log.warn("Q3 handler: Stub implementation - userId={}", userId);

        Q3Metrics metrics = new Q3Metrics(2);
        List<Object> items = new ArrayList<>();
        items.add(new Q3EducationItem("EDU001", "개인정보보호 교육", "2025-01-31", 13));
        items.add(new Q3EducationItem("EDU003", "직장 내 괴롭힘 예방교육", "2025-01-25", 7));

        return new ResolveResponse(
            "Q3", periodStart, periodEnd, updatedAt,
            Map.of("deadline_count", metrics.getDeadline_count()),
            items,
            Map.of(),
            null
        );
    }

    // ---------- Q4: 교육 이어보기 (마지막 시청 영상) ----------
    private ResolveResponse handleQ4(String userId, String periodStart, String periodEnd, String updatedAt) {
        log.info("Q4 handler: userId={}", userId);

        try {
            UUID userUuid = UUID.fromString(userId);

            // education-service에서 마지막 시청 영상 조회
            LastVideoProgressItem lastProgress = educationServiceClient.getLastVideoProgress(userUuid);

            if (lastProgress == null) {
                // 시청 기록 없음
                return new ResolveResponse(
                    "Q4", periodStart, periodEnd, updatedAt,
                    Map.of("progress_percent", 0, "total_watch_seconds", 0),
                    List.of(),
                    Map.of(),
                    new ErrorInfo("NOT_FOUND", "시청 기록이 없습니다.")
                );
            }

            // 성공 응답 생성
            Q4VideoItem videoItem = new Q4VideoItem(
                lastProgress.getEducation_id(),
                lastProgress.getVideo_id(),
                lastProgress.getEducation_title(),
                lastProgress.getVideo_title(),
                lastProgress.getResume_position_seconds(),
                lastProgress.getProgress_percent(),
                lastProgress.getDuration()
            );

            int progressPercent = lastProgress.getProgress_percent() != null ? lastProgress.getProgress_percent() : 0;
            int resumeSeconds = lastProgress.getResume_position_seconds() != null ? lastProgress.getResume_position_seconds() : 0;

            return new ResolveResponse(
                "Q4", periodStart, periodEnd, updatedAt,
                Map.of(
                    "progress_percent", progressPercent,
                    "total_watch_seconds", resumeSeconds
                ),
                List.of(videoItem),
                Map.of(),
                null
            );
        } catch (IllegalArgumentException e) {
            log.warn("Invalid userId format: {}", userId);
            return createErrorResponse("Q4", periodStart, periodEnd, updatedAt,
                "INVALID_USER", "사용자 정보를 확인할 수 없어요.");
        } catch (Exception e) {
            log.error("Error in Q4 handler: userId={}", userId, e);
            return createErrorResponse("Q4", periodStart, periodEnd, updatedAt,
                "SERVICE_ERROR", "데이터를 조회하는 중 오류가 발생했어요.");
        }
    }

    // ---------- Q5: 내 평균 vs 부서/전사 평균 ----------
    private ResolveResponse handleQ5(String userId, String periodStart, String periodEnd, String updatedAt, String targetDeptId) {
        log.info("Q5 handler: userId={}, targetDeptId={}", userId, targetDeptId);

        try {
            UUID userUuid = UUID.fromString(userId);

            // 1. 사용자의 퀴즈 응시 내역 조회
            List<MyAttemptItem> myAttempts = educationServiceClient.getMyAttempts(userUuid);

            // 2. 내 평균 점수 계산 (best attempt 기준)
            double myAverage = myAttempts.stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsBestScore()) && a.getScore() != null)
                .mapToInt(MyAttemptItem::getScore)
                .average()
                .orElse(0.0);

            // 3. 부서별 통계 조회
            List<DepartmentStatsItem> deptStats = educationServiceClient.getDepartmentStats(null);

            // 4. 부서/전사 평균 계산
            double deptAverage = 0.0;
            double companyAverage = 0.0;
            String targetDeptName = "전체";

            if (!deptStats.isEmpty()) {
                // 전사 평균
                companyAverage = deptStats.stream()
                    .filter(d -> d.getAverageScore() != null)
                    .mapToInt(DepartmentStatsItem::getAverageScore)
                    .average()
                    .orElse(0.0);

                // 특정 부서 또는 첫 번째 부서 평균
                if (targetDeptId != null) {
                    deptAverage = deptStats.stream()
                        .filter(d -> targetDeptId.equals(d.getDepartmentName()))
                        .findFirst()
                        .map(d -> d.getAverageScore() != null ? d.getAverageScore().doubleValue() : 0.0)
                        .orElse(companyAverage);
                    targetDeptName = targetDeptId;
                } else if (!deptStats.isEmpty()) {
                    DepartmentStatsItem firstDept = deptStats.get(0);
                    deptAverage = firstDept.getAverageScore() != null ? firstDept.getAverageScore() : 0.0;
                    targetDeptName = firstDept.getDepartmentName();
                }
            }

            Map<String, Object> extra = new HashMap<>();
            extra.put("target_dept_id", targetDeptId != null ? targetDeptId : "ALL");
            extra.put("target_dept_name", targetDeptName);
            extra.put("attempt_count", myAttempts.size());

            return new ResolveResponse(
                "Q5", periodStart, periodEnd, updatedAt,
                Map.of(
                    "my_average", Math.round(myAverage * 10) / 10.0,
                    "dept_average", Math.round(deptAverage * 10) / 10.0,
                    "company_average", Math.round(companyAverage * 10) / 10.0
                ),
                List.of(),
                extra,
                null
            );
        } catch (IllegalArgumentException e) {
            log.warn("Invalid userId format: {}", userId);
            return createErrorResponse("Q5", periodStart, periodEnd, updatedAt,
                "INVALID_USER", "사용자 정보를 확인할 수 없어요.");
        } catch (Exception e) {
            log.error("Error in Q5 handler: userId={}", userId, e);
            return createErrorResponse("Q5", periodStart, periodEnd, updatedAt,
                "SERVICE_ERROR", "데이터를 조회하는 중 오류가 발생했어요.");
        }
    }

    // ---------- Q6: 가장 많이 틀린 보안 토픽 TOP3 (가장 낮은 점수 교육) ----------
    private ResolveResponse handleQ6(String userId, String periodStart, String periodEnd, String updatedAt) {
        log.info("Q6 handler: userId={}", userId);

        try {
            UUID userUuid = UUID.fromString(userId);

            // 1. 사용자의 퀴즈 응시 내역 조회
            List<MyAttemptItem> myAttempts = educationServiceClient.getMyAttempts(userUuid);

            // 2. 교육별 최저 점수 계산 (best attempt 기준, 점수가 낮을수록 오답률 높음)
            Map<UUID, MyAttemptItem> lowestByEducation = new HashMap<>();
            for (MyAttemptItem attempt : myAttempts) {
                if (Boolean.TRUE.equals(attempt.getIsBestScore()) && attempt.getScore() != null) {
                    UUID educationId = attempt.getEducationId();
                    if (!lowestByEducation.containsKey(educationId) ||
                        attempt.getScore() < lowestByEducation.get(educationId).getScore()) {
                        lowestByEducation.put(educationId, attempt);
                    }
                }
            }

            // 3. 점수가 낮은 순으로 정렬하여 TOP 3 추출
            List<MyAttemptItem> sortedByScore = lowestByEducation.values().stream()
                .sorted(Comparator.comparingInt(MyAttemptItem::getScore))
                .limit(3)
                .collect(Collectors.toList());

            // 4. 결과 생성 (점수를 오답률로 변환: 100 - score)
            List<Object> items = new ArrayList<>();
            int rank = 1;
            for (MyAttemptItem attempt : sortedByScore) {
                double wrongRate = 100.0 - attempt.getScore();
                items.add(new Q6TopicItem(rank++, attempt.getEducationTitle(), Math.round(wrongRate * 10) / 10.0));
            }

            Map<String, Object> extra = new HashMap<>();
            extra.put("total_educations", lowestByEducation.size());

            return new ResolveResponse(
                "Q6", periodStart, periodEnd, updatedAt,
                Map.of("topic_count", items.size()),
                items,
                extra,
                null
            );
        } catch (IllegalArgumentException e) {
            log.warn("Invalid userId format: {}", userId);
            return createErrorResponse("Q6", periodStart, periodEnd, updatedAt,
                "INVALID_USER", "사용자 정보를 확인할 수 없어요.");
        } catch (Exception e) {
            log.error("Error in Q6 handler: userId={}", userId, e);
            return createErrorResponse("Q6", periodStart, periodEnd, updatedAt,
                "SERVICE_ERROR", "데이터를 조회하는 중 오류가 발생했어요.");
        }
    }

    // ---------- Q7: 특정 토픽 퀴즈 점수 조회 ----------
    private ResolveResponse handleQ7(String userId, String periodStart, String periodEnd, String updatedAt, String topic) {
        log.info("Q7 handler: userId={}, topic={}", userId, topic);

        if (topic == null || topic.isBlank()) {
            return createErrorResponse("Q7", periodStart, periodEnd, updatedAt,
                "MISSING_TOPIC", "조회할 교육 토픽을 지정해주세요.");
        }

        try {
            UUID userUuid = UUID.fromString(userId);
            TopicScoreResponse score = educationServiceClient.getScoreByTopic(userUuid, topic);

            if (score == null) {
                return createErrorResponse("Q7", periodStart, periodEnd, updatedAt,
                    "SERVICE_ERROR", "퀴즈 정보를 조회할 수 없어요.");
            }

            if (!score.isHasAttempt()) {
                return new ResolveResponse(
                    "Q7", periodStart, periodEnd, updatedAt,
                    Map.of(
                        "topic", topic,
                        "topic_label", score.getTopicLabel() != null ? score.getTopicLabel() : topic,
                        "has_attempt", false,
                        "education_count", score.getEducationCount()
                    ),
                    List.of(),
                    Map.of(),
                    new ErrorInfo("NOT_FOUND", "해당 토픽의 퀴즈 응시 기록이 없어요.")
                );
            }

            // 퀴즈 목록을 items로 변환
            List<Object> items = new ArrayList<>();
            if (score.getItems() != null) {
                for (TopicScoreItem item : score.getItems()) {
                    Map<String, Object> itemMap = new HashMap<>();
                    itemMap.put("education_id", item.getEducationId());
                    itemMap.put("title", item.getTitle());
                    itemMap.put("has_attempt", item.isHasAttempt());
                    itemMap.put("best_score", item.getBestScore() != null ? item.getBestScore() : 0);
                    itemMap.put("passed", item.getPassed() != null ? item.getPassed() : false);
                    itemMap.put("attempt_count", item.getAttemptCount() != null ? item.getAttemptCount() : 0);
                    itemMap.put("pass_score", item.getPassScore() != null ? item.getPassScore() : 0);
                    items.add(itemMap);
                }
            }

            return new ResolveResponse(
                "Q7", periodStart, periodEnd, updatedAt,
                Map.of(
                    "topic", topic,
                    "topic_label", score.getTopicLabel() != null ? score.getTopicLabel() : topic,
                    "has_attempt", true,
                    "education_count", score.getEducationCount(),
                    "attempted_count", score.getAttemptedCount(),
                    "passed_count", score.getPassedCount(),
                    "average_score", score.getAverageScore() != null ? score.getAverageScore() : 0.0
                ),
                items,
                Map.of(),
                null
            );
        } catch (IllegalArgumentException e) {
            log.warn("Invalid userId format: {}", userId);
            return createErrorResponse("Q7", periodStart, periodEnd, updatedAt,
                "INVALID_USER", "사용자 정보를 확인할 수 없어요.");
        } catch (Exception e) {
            log.error("Error in Q7 handler: userId={}, topic={}", userId, topic, e);
            return createErrorResponse("Q7", periodStart, periodEnd, updatedAt,
                "SERVICE_ERROR", "데이터를 조회하는 중 오류가 발생했어요.");
        }
    }

    // ---------- Q8: 특정 토픽 교육 시청 완료 여부 ----------
    private ResolveResponse handleQ8(String userId, String periodStart, String periodEnd, String updatedAt, String topic) {
        log.info("Q8 handler: userId={}, topic={}", userId, topic);

        if (topic == null || topic.isBlank()) {
            return createErrorResponse("Q8", periodStart, periodEnd, updatedAt,
                "MISSING_TOPIC", "조회할 교육 토픽을 지정해주세요.");
        }

        try {
            UUID userUuid = UUID.fromString(userId);
            TopicProgressResponse progress = educationServiceClient.getProgressByTopic(userUuid, topic);

            if (progress == null) {
                return createErrorResponse("Q8", periodStart, periodEnd, updatedAt,
                    "SERVICE_ERROR", "교육 정보를 조회할 수 없어요.");
            }

            // 교육 목록을 items로 변환 (Q8은 시청 완료 여부에 집중)
            List<Object> items = new ArrayList<>();
            if (progress.getItems() != null) {
                for (TopicEducationItem item : progress.getItems()) {
                    items.add(Map.of(
                        "education_id", item.getEducationId(),
                        "title", item.getTitle(),
                        "is_completed", item.isCompleted(),
                        "progress_percent", item.getProgressPercent() != null ? item.getProgressPercent() : 0
                    ));
                }
            }

            return new ResolveResponse(
                "Q8", periodStart, periodEnd, updatedAt,
                Map.of(
                    "topic", topic,
                    "topic_label", progress.getTopicLabel() != null ? progress.getTopicLabel() : topic,
                    "education_count", progress.getEducationCount(),
                    "completed_count", progress.getCompletedCount(),
                    "is_all_completed", progress.isCompleted()
                ),
                items,
                Map.of(),
                null
            );
        } catch (IllegalArgumentException e) {
            log.warn("Invalid userId format: {}", userId);
            return createErrorResponse("Q8", periodStart, periodEnd, updatedAt,
                "INVALID_USER", "사용자 정보를 확인할 수 없어요.");
        } catch (Exception e) {
            log.error("Error in Q8 handler: userId={}, topic={}", userId, topic, e);
            return createErrorResponse("Q8", periodStart, periodEnd, updatedAt,
                "SERVICE_ERROR", "데이터를 조회하는 중 오류가 발생했어요.");
        }
    }

    // ---------- Q9: 이번 주 교육/퀴즈 할 일 ----------
    private ResolveResponse handleQ9(String userId, String periodStart, String periodEnd, String updatedAt) {
        // TODO: education-service와 quiz-service에서 이번 주 할 일 조회
        log.warn("Q9 handler: Stub implementation - userId={}", userId);
        
        Q9Metrics metrics = new Q9Metrics(3);
        List<Object> items = new ArrayList<>();
        items.add(new Q9TodoItem("education", "정보보안 교육", "2025-01-20"));
        items.add(new Q9TodoItem("quiz", "보안 퀴즈", "2025-01-19"));
        items.add(new Q9TodoItem("education", "개인정보보호 교육", "2025-01-21"));

        return new ResolveResponse(
            "Q9", periodStart, periodEnd, updatedAt,
            Map.of("todo_count", metrics.getTodo_count()),
            items,
            Map.of(),
            null
        );
    }

    // ---------- Q11: 남은 연차 일수 ----------
    private ResolveResponse handleQ11(String userId, String periodStart, String periodEnd, String updatedAt) {
        // TODO: HR 시스템에서 연차 정보 조회
        log.warn("Q11 handler: Stub implementation - userId={}", userId);
        
        Q11Metrics metrics = new Q11Metrics(15, 8, 7);

        return new ResolveResponse(
            "Q11", periodStart, periodEnd, updatedAt,
            Map.of(
                "total_days", metrics.getTotal_days(),
                "used_days", metrics.getUsed_days(),
                "remaining_days", metrics.getRemaining_days()
            ),
            List.of(),
            Map.of(),
            null
        );
    }

    // ---------- Q14: 복지/식대 포인트 잔액 ----------
    private ResolveResponse handleQ14(String userId, String periodStart, String periodEnd, String updatedAt) {
        // TODO: HR 시스템에서 복지/식대 포인트 조회
        log.warn("Q14 handler: Stub implementation - userId={}", userId);
        
        Q14Metrics metrics = new Q14Metrics(150000, 280000);

        return new ResolveResponse(
            "Q14", periodStart, periodEnd, updatedAt,
            Map.of(
                "welfare_points", metrics.getWelfare_points(),
                "meal_allowance", metrics.getMeal_allowance()
            ),
            List.of(),
            Map.of(),
            null
        );
    }

    // ---------- Q18: 보안교육(특정 토픽) 완료 여부 ----------
    private ResolveResponse handleQ18(String userId, String periodStart, String periodEnd, String updatedAt, String topic) {
        log.info("Q18 handler: userId={}, topic={}", userId, topic);

        // Q18은 보안교육(정보보호) 완료 여부 - 기본값은 PERSONAL_INFO_PROTECTION
        String effectiveTopic = (topic != null && !topic.isBlank()) ? topic : "PERSONAL_INFO_PROTECTION";

        try {
            UUID userUuid = UUID.fromString(userId);

            // 교육 이수 현황 조회
            TopicProgressResponse progress = educationServiceClient.getProgressByTopic(userUuid, effectiveTopic);
            // 퀴즈 점수 조회
            TopicScoreResponse score = educationServiceClient.getScoreByTopic(userUuid, effectiveTopic);

            if (progress == null) {
                return createErrorResponse("Q18", periodStart, periodEnd, updatedAt,
                    "SERVICE_ERROR", "교육 정보를 조회할 수 없어요.");
            }

            // Q18은 교육 시청 + 퀴즈 통과 여부를 종합
            boolean videoCompleted = progress.isCompleted();
            boolean quizPassed = score != null && score.getPassedCount() > 0 &&
                score.getPassedCount() == score.getEducationCount();

            boolean isFullyCompleted = videoCompleted && quizPassed;

            // 상세 정보 구성
            List<Object> items = new ArrayList<>();
            if (progress.getItems() != null) {
                for (TopicEducationItem item : progress.getItems()) {
                    Map<String, Object> itemMap = new HashMap<>();
                    itemMap.put("education_id", item.getEducationId());
                    itemMap.put("title", item.getTitle());
                    itemMap.put("video_completed", item.isCompleted());
                    itemMap.put("progress_percent", item.getProgressPercent() != null ? item.getProgressPercent() : 0);

                    // 해당 교육의 퀴즈 점수 찾기
                    if (score != null && score.getItems() != null) {
                        for (TopicScoreItem scoreItem : score.getItems()) {
                            if (scoreItem.getEducationId().equals(item.getEducationId())) {
                                itemMap.put("quiz_score", scoreItem.getBestScore() != null ? scoreItem.getBestScore() : 0);
                                itemMap.put("quiz_passed", scoreItem.getPassed() != null ? scoreItem.getPassed() : false);
                                break;
                            }
                        }
                    }
                    items.add(itemMap);
                }
            }

            return new ResolveResponse(
                "Q18", periodStart, periodEnd, updatedAt,
                Map.of(
                    "topic", effectiveTopic,
                    "topic_label", progress.getTopicLabel() != null ? progress.getTopicLabel() : effectiveTopic,
                    "education_count", progress.getEducationCount(),
                    "video_completed_count", progress.getCompletedCount(),
                    "quiz_passed_count", score != null ? score.getPassedCount() : 0,
                    "is_fully_completed", isFullyCompleted
                ),
                items,
                Map.of(),
                null
            );
        } catch (IllegalArgumentException e) {
            log.warn("Invalid userId format: {}", userId);
            return createErrorResponse("Q18", periodStart, periodEnd, updatedAt,
                "INVALID_USER", "사용자 정보를 확인할 수 없어요.");
        } catch (Exception e) {
            log.error("Error in Q18 handler: userId={}, topic={}", userId, topic, e);
            return createErrorResponse("Q18", periodStart, periodEnd, updatedAt,
                "SERVICE_ERROR", "데이터를 조회하는 중 오류가 발생했어요.");
        }
    }

    // ---------- Q19: 특정 토픽 교육 마감일 조회 ----------
    private ResolveResponse handleQ19(String userId, String periodStart, String periodEnd, String updatedAt, String topic) {
        log.info("Q19 handler: userId={}, topic={}", userId, topic);

        if (topic == null || topic.isBlank()) {
            return createErrorResponse("Q19", periodStart, periodEnd, updatedAt,
                "MISSING_TOPIC", "조회할 교육 토픽을 지정해주세요.");
        }

        try {
            UUID userUuid = UUID.fromString(userId);
            TopicDeadlineResponse deadline = educationServiceClient.getDeadlineByTopic(userUuid, topic);

            if (deadline == null) {
                return createErrorResponse("Q19", periodStart, periodEnd, updatedAt,
                    "SERVICE_ERROR", "교육 정보를 조회할 수 없어요.");
            }

            if (!deadline.isHasDeadline()) {
                return new ResolveResponse(
                    "Q19", periodStart, periodEnd, updatedAt,
                    Map.of(
                        "topic", topic,
                        "topic_label", deadline.getTopicLabel() != null ? deadline.getTopicLabel() : topic,
                        "has_deadline", false
                    ),
                    List.of(),
                    Map.of(),
                    new ErrorInfo("NOT_FOUND", "해당 토픽의 마감일이 설정된 교육이 없어요.")
                );
            }

            // 마감일 목록을 items로 변환
            List<Object> items = new ArrayList<>();
            if (deadline.getItems() != null) {
                for (TopicDeadlineItem item : deadline.getItems()) {
                    items.add(Map.of(
                        "education_id", item.getEducationId(),
                        "title", item.getTitle(),
                        "deadline", item.getDeadline(),
                        "is_completed", item.getIsCompleted() != null ? item.getIsCompleted() : false
                    ));
                }
            }

            return new ResolveResponse(
                "Q19", periodStart, periodEnd, updatedAt,
                Map.of(
                    "topic", topic,
                    "topic_label", deadline.getTopicLabel() != null ? deadline.getTopicLabel() : topic,
                    "has_deadline", true,
                    "nearest_deadline", deadline.getNearestDeadline() != null ? deadline.getNearestDeadline() : ""
                ),
                items,
                Map.of(),
                null
            );
        } catch (IllegalArgumentException e) {
            log.warn("Invalid userId format: {}", userId);
            return createErrorResponse("Q19", periodStart, periodEnd, updatedAt,
                "INVALID_USER", "사용자 정보를 확인할 수 없어요.");
        } catch (Exception e) {
            log.error("Error in Q19 handler: userId={}, topic={}", userId, topic, e);
            return createErrorResponse("Q19", periodStart, periodEnd, updatedAt,
                "SERVICE_ERROR", "데이터를 조회하는 중 오류가 발생했어요.");
        }
    }

    // ---------- Q20: 올해 HR 할 일 (미완료) ----------
    private ResolveResponse handleQ20(String userId, String periodStart, String periodEnd, String updatedAt) {
        // TODO: HR 시스템에서 올해 미완료 할 일 조회
        log.warn("Q20 handler: Stub implementation - userId={}", userId);
        
        Q20Metrics metrics = new Q20Metrics(4);
        List<Object> items = new ArrayList<>();
        items.add(new Q20TodoItem("education", "필수 교육 2건", "미완료", null));
        items.add(new Q20TodoItem("document", "연말정산 서류 제출", null, "2025-01-31"));
        items.add(new Q20TodoItem("survey", "직원 만족도 조사", null, "2025-02-28"));
        items.add(new Q20TodoItem("review", "상반기 성과 평가", null, "2025-06-30"));

        return new ResolveResponse(
            "Q20", periodStart, periodEnd, updatedAt,
            Map.of("todo_count", metrics.getTodo_count()),
            items,
            Map.of(),
            null
        );
    }

    // ---------- 에러 응답 생성 ----------
    private ResolveResponse createErrorResponse(String subIntentId, String periodStart, String periodEnd, 
        String updatedAt, String errorType, String errorMessage) {
        return new ResolveResponse(
            subIntentId,
            periodStart,
            periodEnd,
            updatedAt,
            Map.of(),
            List.of(),
            Map.of(),
            new ErrorInfo(errorType, errorMessage)
        );
    }
}

