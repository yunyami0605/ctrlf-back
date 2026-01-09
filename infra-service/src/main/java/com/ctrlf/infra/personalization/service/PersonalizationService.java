package com.ctrlf.infra.personalization.service;

import static com.ctrlf.infra.personalization.dto.PersonalizationDtos.*;

import com.ctrlf.infra.hr.entity.Attendance;
import com.ctrlf.infra.hr.entity.Department;
import com.ctrlf.infra.hr.entity.Employee;
import com.ctrlf.infra.hr.entity.LeaveHistory;
import com.ctrlf.infra.hr.entity.Salary;
import com.ctrlf.infra.hr.entity.WelfarePoint;
import com.ctrlf.infra.hr.entity.WelfarePointUsage;
import com.ctrlf.infra.hr.repository.AttendanceRepository;
import com.ctrlf.infra.hr.repository.DepartmentRepository;
import com.ctrlf.infra.hr.repository.EmployeeRepository;
import com.ctrlf.infra.hr.repository.LeaveHistoryRepository;
import com.ctrlf.infra.hr.repository.SalaryRepository;
import com.ctrlf.infra.hr.repository.WelfarePointRepository;
import com.ctrlf.infra.hr.repository.WelfarePointUsageRepository;
import com.ctrlf.infra.keycloak.service.KeycloakAdminService;
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
import com.ctrlf.infra.personalization.client.EducationServiceClient.IncompleteMandatoryResponse;
import com.ctrlf.infra.personalization.client.EducationServiceClient.IncompleteMandatoryItem;
import com.ctrlf.infra.personalization.client.EducationServiceClient.DeadlinesThisMonthResponse;
import com.ctrlf.infra.personalization.client.EducationServiceClient.DeadlineEducationItem;
import com.ctrlf.infra.personalization.client.EducationServiceClient.TodosThisWeekResponse;
import com.ctrlf.infra.personalization.client.EducationServiceClient.TodoItemResponse;
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
    private static final DateTimeFormatter DATE_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final EducationServiceClient educationServiceClient;
    private final LeaveHistoryRepository leaveHistoryRepository;
    private final WelfarePointRepository welfarePointRepository;
    private final WelfarePointUsageRepository welfarePointUsageRepository;
    private final AttendanceRepository attendanceRepository;
    private final SalaryRepository salaryRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final KeycloakAdminService keycloakAdminService;

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
                case "Q10" -> handleQ10(userId, periodStart, periodEnd, updatedAt);
                case "Q11" -> handleQ11(userId, periodStart, periodEnd, updatedAt);
                case "Q12" -> handleQ12(userId, periodStart, periodEnd, updatedAt);
                case "Q13" -> handleQ13(userId, periodStart, periodEnd, updatedAt);
                case "Q14" -> handleQ14(userId, periodStart, periodEnd, updatedAt);
                case "Q15" -> handleQ15(userId, periodStart, periodEnd, updatedAt);
                case "Q16" -> handleQ16(userId, periodStart, periodEnd, updatedAt);
                case "Q17" -> handleQ17(userId, periodStart, periodEnd, updatedAt);
                case "Q18" -> handleQ18(userId, periodStart, periodEnd, updatedAt, request.getTopic());
                case "Q19" -> handleQ19(userId, periodStart, periodEnd, updatedAt, request.getTopic());
                case "Q20" -> handleQ20(userId, periodStart, periodEnd, updatedAt);
                case "Q21" -> handleQ21(userId, periodStart, periodEnd, updatedAt); // 내 부서만 조회
                case "Q22" -> handleQ22(userId, periodStart, periodEnd, updatedAt); // 내 직급만 조회
                case "Q23" -> handleQ23(userId, periodStart, periodEnd, updatedAt); // 내 이메일만 조회
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
        log.info("Q1 handler: userId={}", userId);

        try {
            UUID userUuid = UUID.fromString(userId);

            // education-service에서 미이수 필수 교육 조회
            IncompleteMandatoryResponse response = educationServiceClient.getIncompleteMandatory(userUuid);

            if (response == null) {
                return createErrorResponse("Q1", periodStart, periodEnd, updatedAt,
                    "SERVICE_ERROR", "교육 정보를 조회할 수 없어요.");
            }

            // items 생성
            List<Object> items = new ArrayList<>();
            if (response.getItems() != null) {
                for (IncompleteMandatoryItem item : response.getItems()) {
                    items.add(new Q1EducationItem(
                        item.getEducationId(),
                        item.getTitle(),
                        item.getDeadline() != null ? item.getDeadline() : "",
                        item.getStatus()
                    ));
                }
            }

            // 사용자 이름 조회 (DB 먼저, 없으면 Keycloak)
            Map<String, Object> extra = new HashMap<>();
            String employeeName = getEmployeeName(userUuid, userId);
            if (employeeName != null) {
                extra.put("employee_name", employeeName);
            }
            
            // Keycloak에서 유저 정보 조회 (직급, 부서, 이메일)
            Map<String, Object> keycloakUserInfo = getUserInfoFromKeycloak(userId);
            if (keycloakUserInfo != null) {
                if (keycloakUserInfo.containsKey("department")) {
                    extra.put("department", keycloakUserInfo.get("department"));
                }
                if (keycloakUserInfo.containsKey("position")) {
                    extra.put("position", keycloakUserInfo.get("position"));
                }
                if (keycloakUserInfo.containsKey("email")) {
                    extra.put("email", keycloakUserInfo.get("email"));
                }
            }

            return new ResolveResponse(
                "Q1", periodStart, periodEnd, updatedAt,
                Map.of(
                    "total_required", response.getTotalRequired(),
                    "completed", response.getCompleted(),
                    "remaining", response.getRemaining()
                ),
                items,
                extra,
                null
            );
        } catch (IllegalArgumentException e) {
            log.warn("Invalid userId format: {}", userId);
            return createErrorResponse("Q1", periodStart, periodEnd, updatedAt,
                "INVALID_USER", "사용자 정보를 확인할 수 없어요.");
        } catch (Exception e) {
            log.error("Error in Q1 handler: userId={}", userId, e);
            return createErrorResponse("Q1", periodStart, periodEnd, updatedAt,
                "SERVICE_ERROR", "데이터를 조회하는 중 오류가 발생했어요.");
        }
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

            // 사용자 이름 조회 (DB 먼저, 없으면 Keycloak)
            Map<String, Object> extra = new HashMap<>();
            String employeeName = getEmployeeName(userUuid, userId);
            if (employeeName != null) {
                extra.put("employee_name", employeeName);
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
                extra,
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
        log.info("Q3 handler: userId={}", userId);

        try {
            UUID userUuid = UUID.fromString(userId);

            // education-service에서 이번 달 마감 교육 조회
            DeadlinesThisMonthResponse response = educationServiceClient.getDeadlinesThisMonth(userUuid);

            if (response == null) {
                return createErrorResponse("Q3", periodStart, periodEnd, updatedAt,
                    "SERVICE_ERROR", "교육 정보를 조회할 수 없어요.");
            }

            // items 생성
            List<Object> items = new ArrayList<>();
            if (response.getItems() != null) {
                for (DeadlineEducationItem item : response.getItems()) {
                    items.add(new Q3EducationItem(
                        item.getEducationId(),
                        item.getTitle(),
                        item.getDeadline() != null ? item.getDeadline() : "",
                        item.getDaysLeft()
                    ));
                }
            }

            // 사용자 이름 조회 (DB 먼저, 없으면 Keycloak)
            Map<String, Object> extra = new HashMap<>();
            String employeeName = getEmployeeName(userUuid, userId);
            if (employeeName != null) {
                extra.put("employee_name", employeeName);
            }

            return new ResolveResponse(
                "Q3", periodStart, periodEnd, updatedAt,
                Map.of("deadline_count", response.getDeadlineCount()),
                items,
                extra,
                null
            );
        } catch (IllegalArgumentException e) {
            log.warn("Invalid userId format: {}", userId);
            return createErrorResponse("Q3", periodStart, periodEnd, updatedAt,
                "INVALID_USER", "사용자 정보를 확인할 수 없어요.");
        } catch (Exception e) {
            log.error("Error in Q3 handler: userId={}", userId, e);
            return createErrorResponse("Q3", periodStart, periodEnd, updatedAt,
                "SERVICE_ERROR", "데이터를 조회하는 중 오류가 발생했어요.");
        }
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

            // 사용자 이름 조회 (DB 먼저, 없으면 Keycloak)
            Map<String, Object> extra = new HashMap<>();
            String employeeName = getEmployeeName(userUuid, userId);
            if (employeeName != null) {
                extra.put("employee_name", employeeName);
            }

            return new ResolveResponse(
                "Q4", periodStart, periodEnd, updatedAt,
                Map.of(
                    "progress_percent", progressPercent,
                    "total_watch_seconds", resumeSeconds
                ),
                List.of(videoItem),
                extra,
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
            // 사용자 이름 조회
            employeeRepository.findByUserUuid(userUuid)
                .ifPresent(emp -> extra.put("employee_name", emp.getName()));

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
            // 사용자 이름 조회
            employeeRepository.findByUserUuid(userUuid)
                .ifPresent(emp -> extra.put("employee_name", emp.getName()));

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

            // 사용자 이름 조회 (DB 먼저, 없으면 Keycloak)
            Map<String, Object> extra = new HashMap<>();
            String employeeName = getEmployeeName(userUuid, userId);
            if (employeeName != null) {
                extra.put("employee_name", employeeName);
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
                extra,
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

            // 사용자 이름 조회 (DB 먼저, 없으면 Keycloak)
            Map<String, Object> extra = new HashMap<>();
            String employeeName = getEmployeeName(userUuid, userId);
            if (employeeName != null) {
                extra.put("employee_name", employeeName);
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
                extra,
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
        log.info("Q9 handler: userId={}", userId);

        try {
            UUID userUuid = UUID.fromString(userId);

            // education-service에서 이번 주 할 일 조회
            TodosThisWeekResponse response = educationServiceClient.getTodosThisWeek(userUuid);

            if (response == null) {
                return createErrorResponse("Q9", periodStart, periodEnd, updatedAt,
                    "SERVICE_ERROR", "할 일 정보를 조회할 수 없어요.");
            }

            // items 생성
            List<Object> items = new ArrayList<>();
            if (response.getItems() != null) {
                for (TodoItemResponse item : response.getItems()) {
                    items.add(new Q9TodoItem(
                        item.getType(),
                        item.getTitle(),
                        item.getDeadline() != null ? item.getDeadline() : ""
                    ));
                }
            }

            // 사용자 이름 조회 (DB 먼저, 없으면 Keycloak)
            Map<String, Object> extra = new HashMap<>();
            String employeeName = getEmployeeName(userUuid, userId);
            if (employeeName != null) {
                extra.put("employee_name", employeeName);
            }

            return new ResolveResponse(
                "Q9", periodStart, periodEnd, updatedAt,
                Map.of("todo_count", response.getTodoCount()),
                items,
                extra,
                null
            );
        } catch (IllegalArgumentException e) {
            log.warn("Invalid userId format: {}", userId);
            return createErrorResponse("Q9", periodStart, periodEnd, updatedAt,
                "INVALID_USER", "사용자 정보를 확인할 수 없어요.");
        } catch (Exception e) {
            log.error("Error in Q9 handler: userId={}", userId, e);
            return createErrorResponse("Q9", periodStart, periodEnd, updatedAt,
                "SERVICE_ERROR", "데이터를 조회하는 중 오류가 발생했어요.");
        }
    }

    // ---------- Q10: 내 근태 현황 조회 ----------
    private ResolveResponse handleQ10(String userId, String periodStart, String periodEnd, String updatedAt) {
        log.info("Q10 handler: userId={}", userId);

        try {
            UUID userUuid = UUID.fromString(userId);
            int currentYear = java.time.Year.now().getValue();
            int currentMonth = java.time.LocalDate.now().getMonthValue();

            // DB에서 근태 기록 조회
            List<Attendance> attendanceList = attendanceRepository.findByUserUuidAndYearMonth(
                userUuid, currentYear, currentMonth);

            // 통계 조회
            Long lateCount = attendanceRepository.countLateByUserUuidAndYearMonth(userUuid, currentYear, currentMonth);
            Long earlyLeaveCount = attendanceRepository.countEarlyLeaveByUserUuidAndYearMonth(userUuid, currentYear, currentMonth);
            Long absentCount = attendanceRepository.countAbsentByUserUuidAndYearMonth(userUuid, currentYear, currentMonth);
            Long remoteCount = attendanceRepository.countRemoteByUserUuidAndYearMonth(userUuid, currentYear, currentMonth);
            Long actualWorkDays = attendanceRepository.countActualWorkDaysByUserUuidAndYearMonth(userUuid, currentYear, currentMonth);
            java.math.BigDecimal overtimeSum = attendanceRepository.sumOvertimeByUserUuidAndYearMonth(userUuid, currentYear, currentMonth);

            // 이번 달 근무일수 계산 (주말 제외)
            java.time.LocalDate firstDay = java.time.LocalDate.of(currentYear, currentMonth, 1);
            java.time.LocalDate today = java.time.LocalDate.now();
            int workDays = 0;
            for (java.time.LocalDate date = firstDay; !date.isAfter(today); date = date.plusDays(1)) {
                java.time.DayOfWeek dow = date.getDayOfWeek();
                if (dow != java.time.DayOfWeek.SATURDAY && dow != java.time.DayOfWeek.SUNDAY) {
                    workDays++;
                }
            }

            // items 생성
            List<Object> items = new ArrayList<>();
            String[] dayNames = {"", "월", "화", "수", "목", "금", "토", "일"};
            for (Attendance att : attendanceList) {
                String dateStr = att.getWorkDate() != null ? att.getWorkDate().toString() : "";
                String dayOfWeek = att.getWorkDate() != null ? dayNames[att.getWorkDate().getDayOfWeek().getValue()] : "";
                String checkIn = att.getCheckIn() != null ? att.getCheckIn().toString().substring(0, 5) : "";
                String checkOut = att.getCheckOut() != null ? att.getCheckOut().toString().substring(0, 5) : "";
                double workHours = att.getWorkHours() != null ? att.getWorkHours().doubleValue() : 0;

                items.add(new Q10AttendanceItem(
                    dateStr, dayOfWeek, checkIn, checkOut, workHours,
                    att.getStatus() != null ? att.getStatus() : "NORMAL",
                    att.getWorkType() != null ? att.getWorkType() : "OFFICE"
                ));
            }

            // 사용자 이름 조회 (DB 먼저, 없으면 Keycloak)
            Map<String, Object> extra = new HashMap<>();
            String employeeName = getEmployeeName(userUuid, userId);
            if (employeeName != null) {
                extra.put("employee_name", employeeName);
            }

            return new ResolveResponse(
                "Q10", periodStart, periodEnd, updatedAt,
                Map.of(
                    "work_days", workDays,
                    "actual_work_days", actualWorkDays != null ? actualWorkDays.intValue() : 0,
                    "late_count", lateCount != null ? lateCount.intValue() : 0,
                    "early_leave_count", earlyLeaveCount != null ? earlyLeaveCount.intValue() : 0,
                    "absent_count", absentCount != null ? absentCount.intValue() : 0,
                    "remote_days", remoteCount != null ? remoteCount.intValue() : 0,
                    "overtime_hours", overtimeSum != null ? overtimeSum.doubleValue() : 0.0
                ),
                items,
                extra,
                null
            );
        } catch (IllegalArgumentException e) {
            log.warn("Invalid userId format: {}", userId);
            return createErrorResponse("Q10", periodStart, periodEnd, updatedAt,
                "INVALID_USER", "사용자 정보를 확인할 수 없어요.");
        } catch (Exception e) {
            log.error("Error in Q10 handler: userId={}", userId, e);
            return createErrorResponse("Q10", periodStart, periodEnd, updatedAt,
                "SERVICE_ERROR", "데이터를 조회하는 중 오류가 발생했어요.");
        }
    }

    // ---------- Q11: 남은 연차 일수 ----------
    private ResolveResponse handleQ11(String userId, String periodStart, String periodEnd, String updatedAt) {
        log.info("Q11 handler: userId={}", userId);

        try {
            UUID userUuid = UUID.fromString(userId);
            int currentYear = java.time.Year.now().getValue();

            // DB에서 연차 사용 이력 조회
            Double usedDays = leaveHistoryRepository.sumDaysByUserUuidAndYear(userUuid, currentYear);
            if (usedDays == null) usedDays = 0.0;

            // Keycloak에서 남은 연차 조회
            int remainingDays = 15; // 기본값
            Map<String, Object> keycloakUserInfo = getUserInfoFromKeycloak(userId);
            if (keycloakUserInfo != null && keycloakUserInfo.containsKey("unusedVacationDays")) {
                String unusedVacationDaysStr = (String) keycloakUserInfo.get("unusedVacationDays");
                if (unusedVacationDaysStr != null && !unusedVacationDaysStr.isBlank()) {
                    try {
                        remainingDays = Integer.parseInt(unusedVacationDaysStr);
                    } catch (NumberFormatException e) {
                        log.warn("Invalid unusedVacationDays format: {}", unusedVacationDaysStr);
                    }
                }
            }
            
            // 총 연차 = 남은 연차 + 사용한 연차
            int totalDays = remainingDays + usedDays.intValue();

            // 사용자 이름 조회 (DB 먼저, 없으면 Keycloak)
            Map<String, Object> extra = new HashMap<>();
            String employeeName = getEmployeeName(userUuid, userId);
            if (employeeName != null) {
                extra.put("employee_name", employeeName);
            }

            return new ResolveResponse(
                "Q11", periodStart, periodEnd, updatedAt,
                Map.of(
                    "total_days", totalDays,
                    "used_days", usedDays,
                    "remaining_days", remainingDays
                ),
                List.of(),
                extra,
                null
            );
        } catch (IllegalArgumentException e) {
            log.warn("Invalid userId format: {}", userId, e);
            return createErrorResponse("Q11", periodStart, periodEnd, updatedAt,
                "INVALID_USER", "사용자 정보를 확인할 수 없어요.");
        } catch (Exception e) {
            log.error("Error in Q11 handler: userId={}, error={}", userId, e.getMessage(), e);
            return createErrorResponse("Q11", periodStart, periodEnd, updatedAt,
                "SERVICE_ERROR", "개인화 정보를 가져오는 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    // ---------- Q12: 연차 사용 이력 조회 ----------
    private ResolveResponse handleQ12(String userId, String periodStart, String periodEnd, String updatedAt) {
        log.info("Q12 handler: userId={}", userId);

        try {
            UUID userUuid = UUID.fromString(userId);
            int currentYear = java.time.Year.now().getValue();

            // DB에서 연차 사용 이력 조회
            List<LeaveHistory> leaveList = leaveHistoryRepository.findByUserUuidOrderByStartDateDesc(userUuid);

            // 올해 사용 일수 합계
            Double usedDays = leaveHistoryRepository.sumDaysByUserUuidAndYear(userUuid, currentYear);
            if (usedDays == null) usedDays = 0.0;

            // 올해 사용 건수
            Long usageCount = leaveHistoryRepository.countByUserUuidAndYear(userUuid, currentYear);
            if (usageCount == null) usageCount = 0L;

            // Keycloak에서 남은 연차 조회
            int remainingDays = 15; // 기본값
            Map<String, Object> keycloakUserInfo = getUserInfoFromKeycloak(userId);
            if (keycloakUserInfo != null && keycloakUserInfo.containsKey("unusedVacationDays")) {
                String unusedVacationDaysStr = (String) keycloakUserInfo.get("unusedVacationDays");
                if (unusedVacationDaysStr != null && !unusedVacationDaysStr.isBlank()) {
                    try {
                        remainingDays = Integer.parseInt(unusedVacationDaysStr);
                    } catch (NumberFormatException e) {
                        log.warn("Invalid unusedVacationDays format: {}", unusedVacationDaysStr);
                    }
                }
            }
            
            // 총 연차 = 남은 연차 + 사용한 연차
            int totalDays = remainingDays + usedDays.intValue();

            // items 생성
            List<Object> items = new ArrayList<>();
            for (LeaveHistory leave : leaveList) {
                String startDateStr = leave.getStartDate() != null ? leave.getStartDate().toString() : "";
                String endDateStr = leave.getEndDate() != null ? leave.getEndDate().toString() : startDateStr;

                items.add(new Q12LeaveItem(
                    leave.getLeaveType(),
                    startDateStr,
                    endDateStr,
                    leave.getDays() != null ? leave.getDays().doubleValue() : 0,
                    leave.getReason() != null ? leave.getReason() : ""
                ));
            }

            // 사용자 이름 조회 (DB 먼저, 없으면 Keycloak)
            Map<String, Object> extra = new HashMap<>();
            String employeeName = getEmployeeName(userUuid, userId);
            if (employeeName != null) {
                extra.put("employee_name", employeeName);
            }

            return new ResolveResponse(
                "Q12", periodStart, periodEnd, updatedAt,
                Map.of(
                    "total_days", totalDays,
                    "used_days", usedDays,
                    "remaining_days", remainingDays,
                    "usage_count", usageCount.intValue()
                ),
                items,
                extra,
                null
            );
        } catch (IllegalArgumentException e) {
            log.warn("Invalid userId format: {}", userId);
            return createErrorResponse("Q12", periodStart, periodEnd, updatedAt,
                "INVALID_USER", "사용자 정보를 확인할 수 없어요.");
        } catch (Exception e) {
            log.error("Error in Q12 handler: userId={}", userId, e);
            return createErrorResponse("Q12", periodStart, periodEnd, updatedAt,
                "SERVICE_ERROR", "데이터를 조회하는 중 오류가 발생했어요.");
        }
    }

    // ---------- Q14: 복지/식대 포인트 잔액 ----------
    private ResolveResponse handleQ14(String userId, String periodStart, String periodEnd, String updatedAt) {
        log.info("Q14 handler: userId={}", userId);

        try {
            UUID userUuid = UUID.fromString(userId);
            int currentYear = java.time.Year.now().getValue();

            // DB에서 복지 포인트 조회
            WelfarePoint welfarePoint = welfarePointRepository.findByUserUuidAndYear(userUuid, currentYear)
                .orElse(null);

            int welfarePoints = 0;
            int mealAllowance = 280000;  // 식대는 별도 테이블 필요, 현재는 고정값

            if (welfarePoint != null) {
                welfarePoints = welfarePoint.getRemaining() != null ? welfarePoint.getRemaining() : 0;
            }

            // 사용자 이름 조회 (DB 먼저, 없으면 Keycloak)
            Map<String, Object> extra = new HashMap<>();
            String employeeName = getEmployeeName(userUuid, userId);
            if (employeeName != null) {
                extra.put("employee_name", employeeName);
            }

            return new ResolveResponse(
                "Q14", periodStart, periodEnd, updatedAt,
                Map.of(
                    "welfare_points", welfarePoints,
                    "meal_allowance", mealAllowance,
                    "total_granted", welfarePoint != null ? welfarePoint.getTotalGranted() : 0,
                    "total_used", welfarePoint != null ? welfarePoint.getTotalUsed() : 0
                ),
                List.of(),
                extra,
                null
            );
        } catch (IllegalArgumentException e) {
            log.warn("Invalid userId format: {}", userId);
            return createErrorResponse("Q14", periodStart, periodEnd, updatedAt,
                "INVALID_USER", "사용자 정보를 확인할 수 없어요.");
        } catch (Exception e) {
            log.error("Error in Q14 handler: userId={}", userId, e);
            return createErrorResponse("Q14", periodStart, periodEnd, updatedAt,
                "SERVICE_ERROR", "데이터를 조회하는 중 오류가 발생했어요.");
        }
    }

    // ---------- Q15: 복지 포인트 사용 내역 조회 ----------
    private ResolveResponse handleQ15(String userId, String periodStart, String periodEnd, String updatedAt) {
        log.info("Q15 handler: userId={}", userId);

        try {
            UUID userUuid = UUID.fromString(userId);
            int currentYear = java.time.Year.now().getValue();

            // DB에서 복지 포인트 잔액 조회
            WelfarePoint welfarePoint = welfarePointRepository.findByUserUuidAndYear(userUuid, currentYear)
                .orElse(null);

            // DB에서 복지 포인트 사용 내역 조회
            List<WelfarePointUsage> usageList = welfarePointUsageRepository.findByUserUuidAndYear(userUuid, currentYear);

            // 사용 건수
            Long usageCount = welfarePointUsageRepository.countByUserUuidAndYear(userUuid, currentYear);
            if (usageCount == null) usageCount = 0L;

            // metrics 구성
            int totalGranted = welfarePoint != null ? welfarePoint.getTotalGranted() : 0;
            int totalUsed = welfarePoint != null ? welfarePoint.getTotalUsed() : 0;
            int remaining = welfarePoint != null ? welfarePoint.getRemaining() : 0;

            // items 생성
            List<Object> items = new ArrayList<>();
            for (WelfarePointUsage usage : usageList) {
                String dateStr = usage.getUsageDate() != null ? usage.getUsageDate().toString() : "";

                items.add(new Q15UsageItem(
                    usage.getCategory(),
                    usage.getMerchant() != null ? usage.getMerchant() : "",
                    usage.getAmount() != null ? usage.getAmount() : 0,
                    dateStr,
                    usage.getDescription() != null ? usage.getDescription() : ""
                ));
            }

            // 사용자 이름 조회 (DB 먼저, 없으면 Keycloak)
            Map<String, Object> extra = new HashMap<>();
            String employeeName = getEmployeeName(userUuid, userId);
            if (employeeName != null) {
                extra.put("employee_name", employeeName);
            }

            return new ResolveResponse(
                "Q15", periodStart, periodEnd, updatedAt,
                Map.of(
                    "total_granted", totalGranted,
                    "total_used", totalUsed,
                    "remaining", remaining,
                    "usage_count", usageCount.intValue()
                ),
                items,
                extra,
                null
            );
        } catch (IllegalArgumentException e) {
            log.warn("Invalid userId format: {}", userId);
            return createErrorResponse("Q15", periodStart, periodEnd, updatedAt,
                "INVALID_USER", "사용자 정보를 확인할 수 없어요.");
        } catch (Exception e) {
            log.error("Error in Q15 handler: userId={}", userId, e);
            return createErrorResponse("Q15", periodStart, periodEnd, updatedAt,
                "SERVICE_ERROR", "데이터를 조회하는 중 오류가 발생했어요.");
        }
    }

    // ---------- Q13: 급여 명세서 요약 ----------
    private ResolveResponse handleQ13(String userId, String periodStart, String periodEnd, String updatedAt) {
        log.info("Q13 handler: userId={}", userId);

        try {
            UUID userUuid = UUID.fromString(userId);
            int currentYear = java.time.Year.now().getValue();
            int currentMonth = java.time.LocalDate.now().getMonthValue();

            // 이번 달이 1월이면 작년 12월 급여 조회
            int targetYear = currentMonth == 1 ? currentYear - 1 : currentYear;
            int targetMonth = currentMonth == 1 ? 12 : currentMonth - 1;

            // DB에서 급여 명세 조회
            Salary salary = salaryRepository.findByUserUuidAndYearMonth(userUuid, targetYear, targetMonth)
                .orElse(null);

            if (salary == null) {
                return new ResolveResponse(
                    "Q13", periodStart, periodEnd, updatedAt,
                    Map.of("pay_month", String.format("%d-%02d", targetYear, targetMonth)),
                    List.of(),
                    Map.of(),
                    new ErrorInfo("NOT_FOUND", "해당 월의 급여 명세를 찾을 수 없어요.")
                );
            }

            // items 생성 (지급/공제 항목)
            List<Object> items = new ArrayList<>();
            items.add(new Q13SalaryItem("지급", "기본급", salary.getBaseSalary() != null ? salary.getBaseSalary() : 0));
            items.add(new Q13SalaryItem("지급", "연장근로수당", salary.getOvertimePay() != null ? salary.getOvertimePay() : 0));
            items.add(new Q13SalaryItem("지급", "상여금", salary.getBonus() != null ? salary.getBonus() : 0));
            items.add(new Q13SalaryItem("지급", "식대", salary.getMealAllowance() != null ? salary.getMealAllowance() : 0));
            items.add(new Q13SalaryItem("지급", "교통비", salary.getTransportAllowance() != null ? salary.getTransportAllowance() : 0));
            items.add(new Q13SalaryItem("공제", "소득세", salary.getIncomeTax() != null ? -salary.getIncomeTax() : 0));
            items.add(new Q13SalaryItem("공제", "지방소득세", salary.getLocalTax() != null ? -salary.getLocalTax() : 0));
            items.add(new Q13SalaryItem("공제", "국민연금", salary.getNationalPension() != null ? -salary.getNationalPension() : 0));
            items.add(new Q13SalaryItem("공제", "건강보험", salary.getHealthInsurance() != null ? -salary.getHealthInsurance() : 0));
            items.add(new Q13SalaryItem("공제", "장기요양보험", salary.getLongTermCare() != null ? -salary.getLongTermCare() : 0));
            items.add(new Q13SalaryItem("공제", "고용보험", salary.getEmploymentInsurance() != null ? -salary.getEmploymentInsurance() : 0));

            Map<String, Object> metrics = new HashMap<>();
            metrics.put("pay_month", String.format("%d-%02d", targetYear, targetMonth));
            metrics.put("base_salary", salary.getBaseSalary() != null ? salary.getBaseSalary() : 0);
            metrics.put("overtime_pay", salary.getOvertimePay() != null ? salary.getOvertimePay() : 0);
            metrics.put("bonus", salary.getBonus() != null ? salary.getBonus() : 0);
            metrics.put("meal_allowance", salary.getMealAllowance() != null ? salary.getMealAllowance() : 0);
            metrics.put("transport_allowance", salary.getTransportAllowance() != null ? salary.getTransportAllowance() : 0);
            metrics.put("total_earnings", salary.getTotalEarnings() != null ? salary.getTotalEarnings() : 0);
            metrics.put("income_tax", salary.getIncomeTax() != null ? salary.getIncomeTax() : 0);
            metrics.put("local_tax", salary.getLocalTax() != null ? salary.getLocalTax() : 0);
            metrics.put("national_pension", salary.getNationalPension() != null ? salary.getNationalPension() : 0);
            metrics.put("health_insurance", salary.getHealthInsurance() != null ? salary.getHealthInsurance() : 0);
            metrics.put("long_term_care", salary.getLongTermCare() != null ? salary.getLongTermCare() : 0);
            metrics.put("employment_insurance", salary.getEmploymentInsurance() != null ? salary.getEmploymentInsurance() : 0);
            metrics.put("total_deductions", salary.getTotalDeductions() != null ? salary.getTotalDeductions() : 0);
            metrics.put("net_pay", salary.getNetPay() != null ? salary.getNetPay() : 0);

            // 사용자 이름 조회 (DB 먼저, 없으면 Keycloak)
            Map<String, Object> extra = new HashMap<>();
            String employeeName = getEmployeeName(userUuid, userId);
            if (employeeName != null) {
                extra.put("employee_name", employeeName);
            }

            return new ResolveResponse(
                "Q13", periodStart, periodEnd, updatedAt,
                metrics,
                items,
                extra,
                null
            );
        } catch (IllegalArgumentException e) {
            log.warn("Invalid userId format: {}", userId);
            return createErrorResponse("Q13", periodStart, periodEnd, updatedAt,
                "INVALID_USER", "사용자 정보를 확인할 수 없어요.");
        } catch (Exception e) {
            log.error("Error in Q13 handler: userId={}", userId, e);
            return createErrorResponse("Q13", periodStart, periodEnd, updatedAt,
                "SERVICE_ERROR", "데이터를 조회하는 중 오류가 발생했어요.");
        }
    }

    // ---------- Q16: 내 인사 정보 조회 ----------
    private ResolveResponse handleQ16(String userId, String periodStart, String periodEnd, String updatedAt) {
        log.info("Q16 handler: userId={}", userId);

        try {
            UUID userUuid = UUID.fromString(userId);

            // DB에서 직원 정보 조회
            Employee employee = employeeRepository.findByUserUuid(userUuid).orElse(null);

            // Employee가 없으면 Keycloak에서 정보 조회
            if (employee == null) {
                log.info("Employee not found in DB, trying Keycloak: userId={}", userId);
                Map<String, Object> keycloakUserInfo = getUserInfoFromKeycloak(userId);
                
                if (keycloakUserInfo == null) {
                    log.warn("Keycloak에서도 사용자 정보를 찾을 수 없음: userId={}", userId);
                    return new ResolveResponse(
                        "Q16", periodStart, periodEnd, updatedAt,
                        Map.of(),
                        List.of(),
                        Map.of(),
                        new ErrorInfo("NOT_FOUND", "인사 정보를 찾을 수 없어요.")
                    );
                }
                
                // Keycloak 정보로 응답 생성
                List<Object> items = new ArrayList<>();
                Map<String, Object> metrics = new HashMap<>();
                Map<String, Object> extra = new HashMap<>();
                
                // 기본 정보
                String fullName = (String) keycloakUserInfo.getOrDefault("fullName", "");
                String employeeNo = (String) keycloakUserInfo.getOrDefault("employeeNo", "");
                String department = (String) keycloakUserInfo.getOrDefault("department", "");
                String position = (String) keycloakUserInfo.getOrDefault("position", "");
                String email = (String) keycloakUserInfo.getOrDefault("email", "");
                String companyEmail = (String) keycloakUserInfo.getOrDefault("companyEmail", "");
                String phoneNumber = (String) keycloakUserInfo.getOrDefault("phoneNumber", "");
                String gender = (String) keycloakUserInfo.getOrDefault("gender", "");
                String age = (String) keycloakUserInfo.getOrDefault("age", "");
                String tenureYears = (String) keycloakUserInfo.getOrDefault("tenureYears", "");
                String hireYear = (String) keycloakUserInfo.getOrDefault("hireYear", "");
                String unusedVacationDays = (String) keycloakUserInfo.getOrDefault("unusedVacationDays", "");
                String overtimeHours = (String) keycloakUserInfo.getOrDefault("overtimeHours", "");
                String performanceScore = (String) keycloakUserInfo.getOrDefault("performanceScore", "");
                String salary = (String) keycloakUserInfo.getOrDefault("salary", "");
                
                // items 생성
                if (!employeeNo.isBlank()) items.add(new Q16EmployeeItem("사원번호", employeeNo));
                if (!fullName.isBlank()) items.add(new Q16EmployeeItem("이름", fullName));
                if (!department.isBlank()) items.add(new Q16EmployeeItem("부서", department));
                if (!position.isBlank()) items.add(new Q16EmployeeItem("직급", position));
                if (!tenureYears.isBlank()) items.add(new Q16EmployeeItem("근속연수", tenureYears + "년"));
                if (!hireYear.isBlank()) items.add(new Q16EmployeeItem("입사년도", hireYear));
                if (!email.isBlank()) items.add(new Q16EmployeeItem("이메일", email));
                if (!companyEmail.isBlank()) items.add(new Q16EmployeeItem("회사이메일", companyEmail));
                if (!phoneNumber.isBlank()) items.add(new Q16EmployeeItem("휴대폰", phoneNumber));
                if (!gender.isBlank()) items.add(new Q16EmployeeItem("성별", gender));
                if (!age.isBlank()) items.add(new Q16EmployeeItem("나이", age));
                if (!unusedVacationDays.isBlank()) items.add(new Q16EmployeeItem("남은 연차", unusedVacationDays + "일"));
                if (!overtimeHours.isBlank()) items.add(new Q16EmployeeItem("초과근무시간", overtimeHours + "시간"));
                if (!performanceScore.isBlank()) items.add(new Q16EmployeeItem("성과점수", performanceScore));
                if (!salary.isBlank()) items.add(new Q16EmployeeItem("급여", salary));
                
                // metrics 생성
                metrics.put("employee_id", employeeNo);
                metrics.put("name", fullName);
                metrics.put("department", department);
                metrics.put("position", position);
                metrics.put("email", email);
                metrics.put("company_email", companyEmail);
                metrics.put("phone", phoneNumber); // 원본 전화번호 (AI Gateway에서 마스킹)
                metrics.put("gender", gender);
                metrics.put("age", age);
                metrics.put("tenure_years", tenureYears);
                metrics.put("hire_year", hireYear);
                metrics.put("unused_vacation_days", unusedVacationDays);
                metrics.put("overtime_hours", overtimeHours);
                metrics.put("performance_score", performanceScore);
                metrics.put("salary", salary);
                
                // extra 생성
                extra.put("department", department);
                extra.put("position", position);
                extra.put("email", email);
                extra.put("employee_name", fullName);
                
                return new ResolveResponse(
                    "Q16", periodStart, periodEnd, updatedAt,
                    metrics,
                    items,
                    extra,
                    null
                );
            }

            // 근속 기간 계산
            int yearsOfService = 0;
            int monthsOfService = 0;
            if (employee.getHireDate() != null) {
                java.time.LocalDate hireDate = employee.getHireDate();
                java.time.LocalDate today = java.time.LocalDate.now();
                java.time.Period period = java.time.Period.between(hireDate, today);
                yearsOfService = period.getYears();
                monthsOfService = period.getMonths();
            }

            // items 생성
            List<Object> items = new ArrayList<>();
            items.add(new Q16EmployeeItem("사원번호", employee.getEmployeeId() != null ? employee.getEmployeeId() : ""));
            items.add(new Q16EmployeeItem("이름", employee.getName() != null ? employee.getName() : ""));
            items.add(new Q16EmployeeItem("부서", employee.getDepartmentName() != null ? employee.getDepartmentName() : ""));
            items.add(new Q16EmployeeItem("직급", employee.getPosition() != null ? employee.getPosition() : ""));
            items.add(new Q16EmployeeItem("직책", employee.getJobTitle() != null ? employee.getJobTitle() : ""));
            items.add(new Q16EmployeeItem("입사일", employee.getHireDate() != null ? employee.getHireDate().toString() : ""));
            items.add(new Q16EmployeeItem("근속연수", yearsOfService + "년 " + monthsOfService + "개월"));
            items.add(new Q16EmployeeItem("이메일", employee.getEmail() != null ? employee.getEmail() : ""));
            if (employee.getPhone() != null && !employee.getPhone().isBlank()) {
                items.add(new Q16EmployeeItem("휴대폰", employee.getPhone()));
            }
            if (employee.getOfficePhone() != null && !employee.getOfficePhone().isBlank()) {
                items.add(new Q16EmployeeItem("사내전화", employee.getOfficePhone()));
            }

            Map<String, Object> metrics = new HashMap<>();
            metrics.put("employee_id", employee.getEmployeeId() != null ? employee.getEmployeeId() : "");
            metrics.put("name", employee.getName() != null ? employee.getName() : "");
            metrics.put("department", employee.getDepartmentName() != null ? employee.getDepartmentName() : "");
            metrics.put("position", employee.getPosition() != null ? employee.getPosition() : "");
            metrics.put("job_title", employee.getJobTitle() != null ? employee.getJobTitle() : "");
            metrics.put("hire_date", employee.getHireDate() != null ? employee.getHireDate().toString() : "");
            metrics.put("years_of_service", yearsOfService);
            metrics.put("months_of_service", monthsOfService);
            metrics.put("email", employee.getEmail() != null ? employee.getEmail() : "");
            metrics.put("phone", employee.getPhone() != null ? employee.getPhone() : ""); // 원본 전화번호 (AI Gateway에서 마스킹)
            metrics.put("office_phone", employee.getOfficePhone() != null ? employee.getOfficePhone() : ""); // 원본 사내전화 (AI Gateway에서 마스킹)

            // extra에 employee_name 추가 (Keycloak에서도 조회)
            Map<String, Object> extra = new HashMap<>();
            String employeeName = getEmployeeName(userUuid, userId);
            if (employeeName != null) {
                extra.put("employee_name", employeeName);
            }

            return new ResolveResponse(
                "Q16", periodStart, periodEnd, updatedAt,
                metrics,
                items,
                extra,
                null
            );
        } catch (IllegalArgumentException e) {
            log.warn("Invalid userId format: {}", userId, e);
            return createErrorResponse("Q16", periodStart, periodEnd, updatedAt,
                "INVALID_USER", "사용자 정보를 확인할 수 없어요.");
        } catch (Exception e) {
            log.error("Error in Q16 handler: userId={}, error={}", userId, e.getMessage(), e);
            return createErrorResponse("Q16", periodStart, periodEnd, updatedAt,
                "SERVICE_ERROR", "개인화 정보를 가져오는 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    // ---------- Q17: 내 팀/부서 정보 조회 ----------
    private ResolveResponse handleQ17(String userId, String periodStart, String periodEnd, String updatedAt) {
        log.info("Q17 handler: userId={}", userId);

        try {
            UUID userUuid = UUID.fromString(userId);

            // 1. 사용자의 직원 정보 조회 (부서 UUID 확인)
            Employee employee = employeeRepository.findByUserUuid(userUuid).orElse(null);

            if (employee == null || employee.getDepartmentUuid() == null) {
                return new ResolveResponse(
                    "Q17", periodStart, periodEnd, updatedAt,
                    Map.of(),
                    List.of(),
                    Map.of(),
                    new ErrorInfo("NOT_FOUND", "소속 부서 정보를 찾을 수 없어요.")
                );
            }

            // 2. 부서 정보 조회
            Department department = departmentRepository.findById(employee.getDepartmentUuid()).orElse(null);

            // 3. 같은 부서 직원 목록 조회
            List<Employee> teamMembers = employeeRepository.findByDepartmentUuid(employee.getDepartmentUuid());
            Long totalMembers = employeeRepository.countByDepartmentUuid(employee.getDepartmentUuid());

            // items 생성 (팀원 목록)
            List<Object> items = new ArrayList<>();
            for (Employee member : teamMembers) {
                boolean isLeader = department != null &&
                    department.getLeaderUuid() != null &&
                    department.getLeaderUuid().equals(member.getUserUuid());

                items.add(new Q17TeamMemberItem(
                    member.getEmployeeId() != null ? member.getEmployeeId() : "",
                    member.getName() != null ? member.getName() : "",
                    member.getPosition() != null ? member.getPosition() : "",
                    member.getJobTitle() != null ? member.getJobTitle() : "",
                    isLeader
                ));
            }

            String departmentName = department != null ? department.getDepartmentName() : employee.getDepartmentName();
            String departmentCode = department != null ? department.getDepartmentCode() : "";
            String teamLead = department != null && department.getLeaderName() != null ? department.getLeaderName() : "";
            String teamLeadPosition = department != null && department.getLeaderPosition() != null ? department.getLeaderPosition() : "";
            String parentDepartment = department != null && department.getParentDepartmentName() != null ? department.getParentDepartmentName() : "";

            // extra에 employee_name 추가
            Map<String, Object> extra = new HashMap<>();
            if (employee.getName() != null) {
                extra.put("employee_name", employee.getName());
            }

            return new ResolveResponse(
                "Q17", periodStart, periodEnd, updatedAt,
                Map.of(
                    "department_name", departmentName != null ? departmentName : "",
                    "department_code", departmentCode,
                    "team_lead", teamLead,
                    "team_lead_position", teamLeadPosition,
                    "total_members", totalMembers != null ? totalMembers.intValue() : 0,
                    "full_time", totalMembers != null ? totalMembers.intValue() : 0,
                    "contract", 0,
                    "parent_department", parentDepartment
                ),
                items,
                extra,
                null
            );
        } catch (IllegalArgumentException e) {
            log.warn("Invalid userId format: {}", userId);
            return createErrorResponse("Q17", periodStart, periodEnd, updatedAt,
                "INVALID_USER", "사용자 정보를 확인할 수 없어요.");
        } catch (Exception e) {
            log.error("Error in Q17 handler: userId={}", userId, e);
            return createErrorResponse("Q17", periodStart, periodEnd, updatedAt,
                "SERVICE_ERROR", "데이터를 조회하는 중 오류가 발생했어요.");
        }
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

            // 사용자 이름 조회 (DB 먼저, 없으면 Keycloak)
            Map<String, Object> extra = new HashMap<>();
            String employeeName = getEmployeeName(userUuid, userId);
            if (employeeName != null) {
                extra.put("employee_name", employeeName);
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
                extra,
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

            // 사용자 이름 조회 (DB 먼저, 없으면 Keycloak)
            Map<String, Object> extra = new HashMap<>();
            String employeeName = getEmployeeName(userUuid, userId);
            if (employeeName != null) {
                extra.put("employee_name", employeeName);
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
                extra,
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
        log.info("Q20 handler: userId={}", userId);

        try {
            UUID userUuid = UUID.fromString(userId);

            List<Object> items = new ArrayList<>();

            // 1. 미이수 필수 교육 조회
            IncompleteMandatoryResponse eduResponse = educationServiceClient.getIncompleteMandatory(userUuid);
            if (eduResponse != null && eduResponse.getRemaining() > 0) {
                String eduTitle = eduResponse.getRemaining() == 1
                    ? "필수 교육 1건"
                    : "필수 교육 " + eduResponse.getRemaining() + "건";
                items.add(new Q20TodoItem("education", eduTitle, "미완료", null));
            }

            // 2. 연차 관련 할 일 (사용 권장 알림 - 연차 10일 이상 남은 경우)
            int currentYear = java.time.Year.now().getValue();
            Double usedDays = leaveHistoryRepository.sumDaysByUserUuidAndYear(userUuid, currentYear);
            if (usedDays == null) usedDays = 0.0;
            int totalDays = 15;  // 기본 연차
            double remainingLeave = totalDays - usedDays;
            if (remainingLeave >= 10) {
                items.add(new Q20TodoItem("leave", "연차 사용 권장 (" + (int)remainingLeave + "일 남음)", null, null));
            }

            // 3. 복지 포인트 관련 할 일 (미사용 잔액이 50% 이상인 경우)
            WelfarePoint welfarePoint = welfarePointRepository.findByUserUuidAndYear(userUuid, currentYear).orElse(null);
            if (welfarePoint != null && welfarePoint.getTotalGranted() > 0) {
                double usageRate = (double) welfarePoint.getTotalUsed() / welfarePoint.getTotalGranted();
                if (usageRate < 0.5) {
                    int remaining = welfarePoint.getRemaining();
                    items.add(new Q20TodoItem("welfare", "복지 포인트 사용 권장 (" + String.format("%,d", remaining) + "원 남음)", null, null));
                }
            }

            // 4. 이번 주 마감 교육/퀴즈 (Q9 데이터 활용)
            TodosThisWeekResponse todoResponse = educationServiceClient.getTodosThisWeek(userUuid);
            if (todoResponse != null && todoResponse.getTodoCount() > 0) {
                items.add(new Q20TodoItem("deadline", "이번 주 마감 교육/퀴즈 " + todoResponse.getTodoCount() + "건", null, null));
            }

            // 사용자 이름 조회 (DB 먼저, 없으면 Keycloak)
            Map<String, Object> extra = new HashMap<>();
            String employeeName = getEmployeeName(userUuid, userId);
            if (employeeName != null) {
                extra.put("employee_name", employeeName);
            }

            return new ResolveResponse(
                "Q20", periodStart, periodEnd, updatedAt,
                Map.of("todo_count", items.size()),
                items,
                extra,
                null
            );
        } catch (IllegalArgumentException e) {
            log.warn("Invalid userId format: {}", userId);
            return createErrorResponse("Q20", periodStart, periodEnd, updatedAt,
                "INVALID_USER", "사용자 정보를 확인할 수 없어요.");
        } catch (Exception e) {
            log.error("Error in Q20 handler: userId={}", userId, e);
            return createErrorResponse("Q20", periodStart, periodEnd, updatedAt,
                "SERVICE_ERROR", "데이터를 조회하는 중 오류가 발생했어요.");
        }
    }

    // ---------- Q21: 내 부서만 조회 ----------
    private ResolveResponse handleQ21(String userId, String periodStart, String periodEnd, String updatedAt) {
        log.info("Q21 handler (부서만): userId={}", userId);

        try {
            UUID userUuid = UUID.fromString(userId);
            
            // DB에서 직원 정보 조회
            Employee employee = employeeRepository.findByUserUuid(userUuid).orElse(null);
            String department = null;
            
            if (employee != null && employee.getDepartmentName() != null) {
                department = employee.getDepartmentName();
            } else {
                // Keycloak에서 부서 정보 조회
                Map<String, Object> keycloakUserInfo = getUserInfoFromKeycloak(userId);
                if (keycloakUserInfo != null) {
                    department = (String) keycloakUserInfo.getOrDefault("department", "");
                }
            }
            
            if (department == null || department.isBlank()) {
                return createErrorResponse("Q21", periodStart, periodEnd, updatedAt,
                    "NOT_FOUND", "부서 정보를 찾을 수 없어요.");
            }
            
            Map<String, Object> extra = new HashMap<>();
            String employeeName = getEmployeeName(userUuid, userId);
            if (employeeName != null) {
                extra.put("employee_name", employeeName);
            }
            
            return new ResolveResponse(
                "Q21", periodStart, periodEnd, updatedAt,
                Map.of("department", department),
                List.of(),
                extra,
                null
            );
        } catch (IllegalArgumentException e) {
            log.warn("Invalid userId format: {}", userId);
            return createErrorResponse("Q21", periodStart, periodEnd, updatedAt,
                "INVALID_USER", "사용자 정보를 확인할 수 없어요.");
        } catch (Exception e) {
            log.error("Error in Q21 handler: userId={}", userId, e);
            return createErrorResponse("Q21", periodStart, periodEnd, updatedAt,
                "SERVICE_ERROR", "개인화 정보를 가져오는 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    // ---------- Q22: 내 직급만 조회 ----------
    private ResolveResponse handleQ22(String userId, String periodStart, String periodEnd, String updatedAt) {
        log.info("Q22 handler (직급만): userId={}", userId);

        try {
            UUID userUuid = UUID.fromString(userId);
            
            // DB에서 직원 정보 조회
            Employee employee = employeeRepository.findByUserUuid(userUuid).orElse(null);
            String position = null;
            
            if (employee != null && employee.getPosition() != null) {
                position = employee.getPosition();
            } else {
                // Keycloak에서 직급 정보 조회
                Map<String, Object> keycloakUserInfo = getUserInfoFromKeycloak(userId);
                if (keycloakUserInfo != null) {
                    position = (String) keycloakUserInfo.getOrDefault("position", "");
                }
            }
            
            if (position == null || position.isBlank()) {
                return createErrorResponse("Q22", periodStart, periodEnd, updatedAt,
                    "NOT_FOUND", "직급 정보를 찾을 수 없어요.");
            }
            
            Map<String, Object> extra = new HashMap<>();
            String employeeName = getEmployeeName(userUuid, userId);
            if (employeeName != null) {
                extra.put("employee_name", employeeName);
            }
            
            return new ResolveResponse(
                "Q22", periodStart, periodEnd, updatedAt,
                Map.of("position", position),
                List.of(),
                extra,
                null
            );
        } catch (IllegalArgumentException e) {
            log.warn("Invalid userId format: {}", userId);
            return createErrorResponse("Q22", periodStart, periodEnd, updatedAt,
                "INVALID_USER", "사용자 정보를 확인할 수 없어요.");
        } catch (Exception e) {
            log.error("Error in Q22 handler: userId={}", userId, e);
            return createErrorResponse("Q22", periodStart, periodEnd, updatedAt,
                "SERVICE_ERROR", "개인화 정보를 가져오는 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    // ---------- Q23: 내 이메일만 조회 ----------
    private ResolveResponse handleQ23(String userId, String periodStart, String periodEnd, String updatedAt) {
        log.info("Q23 handler (이메일만): userId={}", userId);

        try {
            UUID userUuid = UUID.fromString(userId);
            
            // DB에서 직원 정보 조회
            Employee employee = employeeRepository.findByUserUuid(userUuid).orElse(null);
            String email = null;
            
            if (employee != null && employee.getEmail() != null) {
                email = employee.getEmail();
            } else {
                // Keycloak에서 이메일 정보 조회
                Map<String, Object> keycloakUserInfo = getUserInfoFromKeycloak(userId);
                if (keycloakUserInfo != null) {
                    email = (String) keycloakUserInfo.getOrDefault("email", "");
                    if (email.isBlank()) {
                        email = (String) keycloakUserInfo.getOrDefault("companyEmail", "");
                    }
                }
            }
            
            if (email == null || email.isBlank()) {
                return createErrorResponse("Q23", periodStart, periodEnd, updatedAt,
                    "NOT_FOUND", "이메일 정보를 찾을 수 없어요.");
            }
            
            Map<String, Object> extra = new HashMap<>();
            String employeeName = getEmployeeName(userUuid, userId);
            if (employeeName != null) {
                extra.put("employee_name", employeeName);
            }
            
            return new ResolveResponse(
                "Q23", periodStart, periodEnd, updatedAt,
                Map.of("email", email),
                List.of(),
                extra,
                null
            );
        } catch (IllegalArgumentException e) {
            log.warn("Invalid userId format: {}", userId);
            return createErrorResponse("Q23", periodStart, periodEnd, updatedAt,
                "INVALID_USER", "사용자 정보를 확인할 수 없어요.");
        } catch (Exception e) {
            log.error("Error in Q23 handler: userId={}", userId, e);
            return createErrorResponse("Q23", periodStart, periodEnd, updatedAt,
                "SERVICE_ERROR", "개인화 정보를 가져오는 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    // ---------- Keycloak 유저 정보 조회 ----------
    
    /**
     * Keycloak에서 개인화를 위한 사용자 정보를 조회합니다.
     * 직급, 부서, 이메일 정보를 반환합니다.
     * 
     * @param userId Keycloak 사용자 ID
     * @return 사용자 정보 Map (department, position, email 포함), 조회 실패 시 null
     */
    private Map<String, Object> getUserInfoFromKeycloak(String userId) {
        try {
            return keycloakAdminService.getUserInfoForPersonalization(userId);
        } catch (Exception e) {
            log.error("Keycloak에서 사용자 정보 조회 실패: userId={}, error={}", userId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 사용자 이름을 조회합니다 (DB 먼저, 없으면 Keycloak).
     * 
     * @param userUuid 사용자 UUID
     * @param userId Keycloak 사용자 ID
     * @return 사용자 이름, 없으면 null
     */
    private String getEmployeeName(UUID userUuid, String userId) {
        // DB에서 먼저 조회
        Employee employee = employeeRepository.findByUserUuid(userUuid).orElse(null);
        if (employee != null && employee.getName() != null) {
            return employee.getName();
        }
        
        // Keycloak에서 이름 조회
        Map<String, Object> keycloakUserInfo = getUserInfoFromKeycloak(userId);
        if (keycloakUserInfo != null) {
            String fullName = (String) keycloakUserInfo.getOrDefault("fullName", "");
            if (!fullName.isBlank()) {
                return fullName;
            }
        }
        
        return null;
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

