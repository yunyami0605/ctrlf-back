package com.ctrlf.chat.service;

import com.ctrlf.chat.dto.response.ChatDashboardResponse;
import com.ctrlf.chat.repository.ChatMessageRepository;
import com.ctrlf.chat.telemetry.entity.TelemetryEvent;
import com.ctrlf.chat.telemetry.repository.TelemetryEventRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 챗봇 관리자 대시보드 서비스 구현체
 * 
 * <p>텔레메트리 이벤트 기반으로 대시보드 데이터를 제공합니다.</p>
 * 
 * @author CtrlF Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatDashboardServiceImpl implements ChatDashboardService {

    private final ChatMessageRepository chatMessageRepository;
    private final TelemetryEventRepository telemetryEventRepository;

    private static final Map<String, String> DOMAIN_LABEL_MAP = Map.of(
        "POLICY", "규정 안내",
        "FAQ", "FAQ",
        "EDUCATION", "교육",
        "QUIZ", "퀴즈",
        "ETC", "기타"
    );

    @Override
    public ChatDashboardResponse.DashboardSummaryResponse getDashboardSummary(
        String period,
        String dept,
        Boolean refresh
    ) {
        // 기본값 처리
        if (period == null || period.isBlank()) {
            period = "30d";
        }
        if (dept == null || dept.isBlank()) {
            dept = "all";
        }

        // 기간 계산
        Instant[] periodRange = calculatePeriodRange(period);
        Instant startDate = periodRange[0];
        Instant endDate = periodRange[1];
        long periodDays = (endDate.toEpochMilli() - startDate.toEpochMilli()) / (1000 * 60 * 60 * 24);
        if (periodDays == 0) {
            periodDays = 1; // today인 경우
        }

        // dept 필터 변환
        String deptId = "all".equals(dept) ? "all" : dept;

        // CHAT_TURN 이벤트 조회
        List<TelemetryEvent> chatTurnEvents = telemetryEventRepository
            .findByEventTypeAndPeriodAndDept("CHAT_TURN", startDate, endDate, deptId);

        // 오늘 질문 수 (CHAT_TURN 이벤트 개수)
        Instant todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant todayEnd = todayStart.plusSeconds(24 * 60 * 60);
        List<TelemetryEvent> todayEvents = telemetryEventRepository
            .findByEventTypeAndPeriodAndDept("CHAT_TURN", todayStart, todayEnd, deptId);
        Long todayQuestionCount = (long) todayEvents.size();

        // 기간 내 질문 수
        Long periodQuestionCount = (long) chatTurnEvents.size();

        // 기간 내 일평균 질문 수
        Long periodDailyAvgQuestionCount = periodDays > 0 
            ? periodQuestionCount / periodDays 
            : 0L;

        // 활성 사용자 수 (고유 userId 개수)
        Set<String> uniqueUserIds = chatTurnEvents.stream()
            .map(TelemetryEvent::getUserId)
            .collect(Collectors.toSet());
        Long activeUsers = (long) uniqueUserIds.size();

        // 평균 응답 시간 (latencyMsTotal)
        double totalLatency = 0.0;
        int latencyCount = 0;
        for (TelemetryEvent event : chatTurnEvents) {
            try {
                Object payloadObj = event.getPayload();
                if (payloadObj == null) {
                    continue;
                }
                // payload가 String인 경우 처리
                if (payloadObj instanceof String) {
                    log.debug("[대시보드 요약] payload가 String 타입입니다: eventId={}", 
                        event.getEventId());
                    continue;
                }
                // payload가 Map인 경우에만 처리
                if (!(payloadObj instanceof Map)) {
                    log.debug("[대시보드 요약] payload가 Map 타입이 아닙니다: eventId={}, type={}", 
                        event.getEventId(), payloadObj.getClass().getName());
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = (Map<String, Object>) payloadObj;
                Object latencyObj = payload.get("latencyMsTotal");
                if (latencyObj instanceof Number) {
                    totalLatency += ((Number) latencyObj).doubleValue();
                    latencyCount++;
                }
            } catch (Exception e) {
                log.debug("[대시보드 요약] 이벤트 payload 파싱 실패: eventId={}, error={}", 
                    event.getEventId(), e.getMessage());
            }
        }
        Long avgLatencyMs = latencyCount > 0 ? (long) (totalLatency / latencyCount) : 0L;

        // PII 감지 비율 (piiDetectedInput 또는 piiDetectedOutput)
        int piiDetectedCount = 0;
        int totalCount = chatTurnEvents.size();
        for (TelemetryEvent event : chatTurnEvents) {
            try {
                Object payloadObj = event.getPayload();
                if (payloadObj == null) {
                    continue;
                }
                // payload가 String인 경우 처리
                if (payloadObj instanceof String) {
                    log.debug("[대시보드 요약] payload가 String 타입입니다: eventId={}", 
                        event.getEventId());
                    continue;
                }
                // payload가 Map인 경우에만 처리
                if (!(payloadObj instanceof Map)) {
                    log.debug("[대시보드 요약] payload가 Map 타입이 아닙니다: eventId={}, type={}", 
                        event.getEventId(), payloadObj.getClass().getName());
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = (Map<String, Object>) payloadObj;
                Boolean piiInput = (Boolean) payload.get("piiDetectedInput");
                Boolean piiOutput = (Boolean) payload.get("piiDetectedOutput");
                if (Boolean.TRUE.equals(piiInput) || Boolean.TRUE.equals(piiOutput)) {
                    piiDetectedCount++;
                }
            } catch (Exception e) {
                log.debug("[대시보드 요약] 이벤트 payload 파싱 실패: eventId={}, error={}", 
                    event.getEventId(), e.getMessage());
            }
        }
        Double piiDetectRate = totalCount > 0 ? (double) piiDetectedCount / totalCount : 0.0;

        // 에러율 (errorCode가 null이 아닌 경우)
        int errorCount = 0;
        for (TelemetryEvent event : chatTurnEvents) {
            try {
                Object payloadObj = event.getPayload();
                if (payloadObj == null) {
                    continue;
                }
                // payload가 String인 경우 처리
                if (payloadObj instanceof String) {
                    log.debug("[대시보드 요약] payload가 String 타입입니다: eventId={}", 
                        event.getEventId());
                    continue;
                }
                // payload가 Map인 경우에만 처리
                if (!(payloadObj instanceof Map)) {
                    log.debug("[대시보드 요약] payload가 Map 타입이 아닙니다: eventId={}, type={}", 
                        event.getEventId(), payloadObj.getClass().getName());
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = (Map<String, Object>) payloadObj;
                Object errorCode = payload.get("errorCode");
                if (errorCode != null) {
                    errorCount++;
                }
            } catch (Exception e) {
                log.debug("[대시보드 요약] 이벤트 payload 파싱 실패: eventId={}, error={}", 
                    event.getEventId(), e.getMessage());
            }
        }
        Double errorRate = totalCount > 0 ? (double) errorCount / totalCount : 0.0;

        // 만족도/불만족도 계산 (텔레메트리 FEEDBACK 이벤트 기반)
        List<TelemetryEvent> feedbackEvents = telemetryEventRepository
            .findByEventTypeAndPeriodAndDept("FEEDBACK", startDate, endDate, deptId);
        
        long likeCount = 0;
        long dislikeCount = 0;
        for (TelemetryEvent event : feedbackEvents) {
            try {
                Object payloadObj = event.getPayload();
                if (payloadObj == null) {
                    continue;
                }
                // payload가 String인 경우 처리
                if (payloadObj instanceof String) {
                    log.debug("[대시보드 요약] 피드백 이벤트 payload가 String 타입입니다: eventId={}", 
                        event.getEventId());
                    continue;
                }
                // payload가 Map인 경우에만 처리
                if (!(payloadObj instanceof Map)) {
                    log.debug("[대시보드 요약] 피드백 이벤트 payload가 Map 타입이 아닙니다: eventId={}, type={}", 
                        event.getEventId(), payloadObj.getClass().getName());
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = (Map<String, Object>) payloadObj;
                String feedback = (String) payload.get("feedback");
                if ("like".equals(feedback)) {
                    likeCount++;
                } else if ("dislike".equals(feedback)) {
                    dislikeCount++;
                }
            } catch (Exception e) {
                log.debug("[대시보드 요약] 피드백 이벤트 payload 파싱 실패: eventId={}, error={}", 
                    event.getEventId(), e.getMessage());
            }
        }
        
        Double satisfactionRate = null;
        Double dislikeRate = null;
        long totalFeedback = likeCount + dislikeCount;
        if (totalFeedback > 0) {
            satisfactionRate = (double) likeCount / totalFeedback;
            dislikeRate = (double) dislikeCount / totalFeedback;
        }

        // RAG 사용 비율 (ragUsed)
        int ragUsedCount = 0;
        for (TelemetryEvent event : chatTurnEvents) {
            try {
                Object payloadObj = event.getPayload();
                if (payloadObj == null) {
                    continue;
                }
                // payload가 String인 경우 처리
                if (payloadObj instanceof String) {
                    log.debug("[대시보드 요약] payload가 String 타입입니다: eventId={}", 
                        event.getEventId());
                    continue;
                }
                // payload가 Map인 경우에만 처리
                if (!(payloadObj instanceof Map)) {
                    log.debug("[대시보드 요약] payload가 Map 타입이 아닙니다: eventId={}, type={}", 
                        event.getEventId(), payloadObj.getClass().getName());
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = (Map<String, Object>) payloadObj;
                Boolean ragUsed = (Boolean) payload.get("ragUsed");
                if (Boolean.TRUE.equals(ragUsed)) {
                    ragUsedCount++;
                }
            } catch (Exception e) {
                log.debug("[대시보드 요약] 이벤트 payload 파싱 실패: eventId={}, error={}", 
                    event.getEventId(), e.getMessage());
            }
        }
        Double ragUsageRate = totalCount > 0 ? (double) ragUsedCount / totalCount : 0.0;

        return new ChatDashboardResponse.DashboardSummaryResponse(
            period,
            dept,
            todayQuestionCount,
            periodQuestionCount,
            periodDailyAvgQuestionCount,
            activeUsers,
            avgLatencyMs,
            piiDetectRate,
            errorRate,
            satisfactionRate,
            dislikeRate,
            ragUsageRate
        );
    }

    @Override
    public ChatDashboardResponse.TrendsResponse getTrends(
        String period,
        String dept,
        String bucket,
        Boolean refresh
    ) {
        // 기본값 처리
        if (period == null || period.isBlank()) {
            period = "30d";
        }
        if (dept == null || dept.isBlank()) {
            dept = "all";
        }
        if (bucket == null || bucket.isBlank()) {
            bucket = "week";
        }

        // 기간 계산
        Instant[] periodRange = calculatePeriodRange(period);
        Instant startDate = periodRange[0];
        Instant endDate = periodRange[1];

        // dept 필터 변환
        String deptId = "all".equals(dept) ? "all" : dept;

        // CHAT_TURN 이벤트 조회
        List<TelemetryEvent> chatTurnEvents = telemetryEventRepository
            .findByEventTypeAndPeriodAndDept("CHAT_TURN", startDate, endDate, deptId);

        // bucket별 집계
        Map<String, List<TelemetryEvent>> bucketMap = new HashMap<>();
        for (TelemetryEvent event : chatTurnEvents) {
            String bucketKey = "day".equals(bucket) 
                ? getDayKey(event.getOccurredAt())
                : getWeekKey(event.getOccurredAt());
            bucketMap.computeIfAbsent(bucketKey, k -> new ArrayList<>()).add(event);
        }

        // 시리즈 데이터 생성
        List<ChatDashboardResponse.TrendsSeriesItem> series = new ArrayList<>();
        for (Map.Entry<String, List<TelemetryEvent>> entry : bucketMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toList())) {
            String bucketStart = entry.getKey();
            List<TelemetryEvent> events = entry.getValue();
            Long questionCount = (long) events.size();
            
            // 에러율 계산
            int errorCount = 0;
            for (TelemetryEvent event : events) {
                try {
                    Object payloadObj = event.getPayload();
                    if (payloadObj == null) {
                        continue;
                    }
                    // payload가 String인 경우 처리
                    if (payloadObj instanceof String) {
                        log.debug("[대시보드 추이] payload가 String 타입입니다: eventId={}", 
                            event.getEventId());
                        continue;
                    }
                    // payload가 Map인 경우에만 처리
                    if (!(payloadObj instanceof Map)) {
                        log.debug("[대시보드 추이] payload가 Map 타입이 아닙니다: eventId={}, type={}", 
                            event.getEventId(), payloadObj.getClass().getName());
                        continue;
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> payload = (Map<String, Object>) payloadObj;
                    Object errorCode = payload.get("errorCode");
                    if (errorCode != null) {
                        errorCount++;
                    }
                } catch (Exception e) {
                    log.debug("[대시보드 추이] 이벤트 payload 파싱 실패: eventId={}, error={}", 
                        event.getEventId(), e.getMessage());
                }
            }
            Double errorRate = questionCount > 0 ? (double) errorCount / questionCount : 0.0;

            series.add(new ChatDashboardResponse.TrendsSeriesItem(
                bucketStart,
                questionCount,
                errorRate
            ));
        }

        return new ChatDashboardResponse.TrendsResponse(bucket, series);
    }

    @Override
    public ChatDashboardResponse.DomainShareResponse getDomainShare(
        String period,
        String dept,
        Boolean refresh
    ) {
        // 기본값 처리
        if (period == null || period.isBlank()) {
            period = "30d";
        }
        if (dept == null || dept.isBlank()) {
            dept = "all";
        }

        // 기간 계산
        Instant[] periodRange = calculatePeriodRange(period);
        Instant startDate = periodRange[0];
        Instant endDate = periodRange[1];

        // dept 필터 변환
        String deptId = "all".equals(dept) ? "all" : dept;

        // CHAT_TURN 이벤트 조회
        List<TelemetryEvent> chatTurnEvents = telemetryEventRepository
            .findByEventTypeAndPeriodAndDept("CHAT_TURN", startDate, endDate, deptId);

        // 도메인별 질문 수 집계
        Map<String, Long> domainCountMap = new HashMap<>();
        for (TelemetryEvent event : chatTurnEvents) {
            try {
                Object payloadObj = event.getPayload();
                if (payloadObj == null) {
                    domainCountMap.merge("ETC", 1L, Long::sum);
                    continue;
                }
                // payload가 String인 경우 처리
                if (payloadObj instanceof String) {
                    log.debug("[도메인별 질문 비율 조회] payload가 String 타입입니다: eventId={}", 
                        event.getEventId());
                    domainCountMap.merge("ETC", 1L, Long::sum);
                    continue;
                }
                // payload가 Map인 경우에만 처리
                if (!(payloadObj instanceof Map)) {
                    log.debug("[도메인별 질문 비율 조회] payload가 Map 타입이 아닙니다: eventId={}, type={}", 
                        event.getEventId(), payloadObj.getClass().getName());
                    domainCountMap.merge("ETC", 1L, Long::sum);
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = (Map<String, Object>) payloadObj;
                String domain = (String) payload.get("domain");
                if (domain == null || domain.isBlank()) {
                    domain = "ETC";
                }
                domain = domain.toUpperCase().trim();
                if ("SECURITY".equals(domain) || "SEC_POLICY".equals(domain)) {
                    domain = "POLICY";
                }
                domainCountMap.merge(domain, 1L, Long::sum);
            } catch (Exception e) {
                log.debug("[도메인별 질문 비율 조회] 이벤트 payload 파싱 실패: eventId={}, error={}", 
                    event.getEventId(), e.getMessage());
                domainCountMap.merge("ETC", 1L, Long::sum);
            }
        }

        long totalCount = chatTurnEvents.size();

        // Map을 List로 변환하고 share 계산
        final long finalTotalCount = totalCount;
        List<ChatDashboardResponse.DomainShareItem> items = domainCountMap.entrySet().stream()
            .map(entry -> {
                String domain = entry.getKey();
                Long questionCount = entry.getValue();
                // share는 0~1 범위
                Double share = finalTotalCount > 0 ? (questionCount.doubleValue() / finalTotalCount) : 0.0;
                String label = DOMAIN_LABEL_MAP.getOrDefault(domain, "기타");
                return new ChatDashboardResponse.DomainShareItem(
                    domain,
                    label,
                    questionCount,
                    share
                );
            })
            .sorted((a, b) -> Double.compare(b.getShare(), a.getShare())) // share 기준 내림차순 정렬
            .collect(Collectors.toList());

        return new ChatDashboardResponse.DomainShareResponse(items);
    }

    /**
     * 기간 문자열을 Instant 범위로 변환
     * 
     * @param period 기간 (today | 7d | 30d | 90d)
     * @return [시작 시각, 종료 시각]
     */
    private Instant[] calculatePeriodRange(String period) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate;

        switch (period) {
            case "today":
                startDate = endDate;
                break;
            case "7d":
                startDate = endDate.minusDays(7);
                break;
            case "30d":
                startDate = endDate.minusDays(30);
                break;
            case "90d":
                startDate = endDate.minusDays(90);
                break;
            default:
                startDate = endDate.minusDays(30);
        }

        return new Instant[] {
            startDate.atStartOfDay(ZoneId.systemDefault()).toInstant(),
            endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        };
    }

    /**
     * 일별 버킷 키 생성 (YYYY-MM-DD)
     */
    private String getDayKey(Instant instant) {
        return instant.atZone(ZoneId.systemDefault()).toLocalDate().toString();
    }

    /**
     * 주별 버킷 키 생성 (YYYY-MM-DD, 주의 시작일)
     */
    private String getWeekKey(Instant instant) {
        LocalDate date = instant.atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate weekStart = date.minusDays(date.getDayOfWeek().getValue() - 1);
        return weekStart.toString();
    }
}
