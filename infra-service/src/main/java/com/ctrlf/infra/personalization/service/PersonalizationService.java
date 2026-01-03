package com.ctrlf.infra.personalization.service;

import static com.ctrlf.infra.personalization.dto.PersonalizationDtos.*;

import com.ctrlf.infra.personalization.dto.PersonalizationDtos;
import com.ctrlf.infra.personalization.util.PeriodCalculator;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
                case "Q3" -> handleQ3(userId, periodStart, periodEnd, updatedAt);
                case "Q5" -> handleQ5(userId, periodStart, periodEnd, updatedAt, targetDeptId);
                case "Q6" -> handleQ6(userId, periodStart, periodEnd, updatedAt);
                case "Q9" -> handleQ9(userId, periodStart, periodEnd, updatedAt);
                case "Q11" -> handleQ11(userId, periodStart, periodEnd, updatedAt);
                case "Q14" -> handleQ14(userId, periodStart, periodEnd, updatedAt);
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

    // ---------- Q5: 내 평균 vs 부서/전사 평균 ----------
    private ResolveResponse handleQ5(String userId, String periodStart, String periodEnd, String updatedAt, String targetDeptId) {
        // TODO: quiz-service에서 평균 점수 조회
        log.warn("Q5 handler: Stub implementation - userId={}, targetDeptId={}", userId, targetDeptId);
        
        Q5Metrics metrics = new Q5Metrics(85.5, 82.3, 80.1);
        Map<String, Object> extra = new HashMap<>();
        extra.put("target_dept_id", targetDeptId != null ? targetDeptId : "D001");
        extra.put("target_dept_name", targetDeptId != null ? "개발팀" : "내 부서");

        return new ResolveResponse(
            "Q5", periodStart, periodEnd, updatedAt,
            Map.of(
                "my_average", metrics.getMy_average(),
                "dept_average", metrics.getDept_average(),
                "company_average", metrics.getCompany_average()
            ),
            List.of(),
            extra,
            null
        );
    }

    // ---------- Q6: 가장 많이 틀린 보안 토픽 TOP3 ----------
    private ResolveResponse handleQ6(String userId, String periodStart, String periodEnd, String updatedAt) {
        // TODO: quiz-service에서 오답률 높은 토픽 조회
        log.warn("Q6 handler: Stub implementation - userId={}", userId);
        
        List<Object> items = new ArrayList<>();
        items.add(new Q6TopicItem(1, "피싱 메일 식별", 35.2));
        items.add(new Q6TopicItem(2, "비밀번호 정책", 28.5));
        items.add(new Q6TopicItem(3, "데이터 암호화", 22.1));

        return new ResolveResponse(
            "Q6", periodStart, periodEnd, updatedAt,
            Map.of(),
            items,
            Map.of(),
            null
        );
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

