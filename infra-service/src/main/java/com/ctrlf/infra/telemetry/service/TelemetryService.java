package com.ctrlf.infra.telemetry.service;

import com.ctrlf.infra.telemetry.dto.TelemetryDtos;
import com.ctrlf.infra.telemetry.entity.TelemetryEvent;
import com.ctrlf.infra.telemetry.repository.TelemetryEventRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 텔레메트리 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TelemetryService {

    private final TelemetryEventRepository telemetryEventRepository;

    /**
     * 텔레메트리 이벤트 수집 (배치 처리, Idempotent)
     */
    public TelemetryDtos.TelemetryEventResponse collectEvents(
        TelemetryDtos.TelemetryEventRequest request
    ) {
        int received = request.getEvents().size();
        int accepted = 0;
        int rejected = 0;
        List<TelemetryDtos.ErrorItem> errors = new ArrayList<>();

        for (TelemetryDtos.EventItem eventItem : request.getEvents()) {
            try {
                // Idempotency 체크
                if (telemetryEventRepository.findByEventId(eventItem.getEventId()).isPresent()) {
                    // 이미 존재하는 이벤트는 무시 (Idempotent)
                    accepted++;
                    continue;
                }

                // 이벤트 저장
                TelemetryEvent event = new TelemetryEvent();
                event.setEventId(eventItem.getEventId());
                event.setSource(request.getSource());
                event.setSentAt(request.getSentAt());
                event.setEventType(eventItem.getEventType());
                event.setTraceId(eventItem.getTraceId());
                event.setConversationId(eventItem.getConversationId());
                event.setTurnId(eventItem.getTurnId());
                event.setUserId(eventItem.getUserId());
                event.setDeptId(eventItem.getDeptId());
                event.setOccurredAt(eventItem.getOccurredAt());
                event.setPayload(eventItem.getPayload());
                event.setReceivedAt(Instant.now());

                telemetryEventRepository.save(event);
                accepted++;

            } catch (Exception e) {
                rejected++;
                errors.add(new TelemetryDtos.ErrorItem(
                    eventItem.getEventId(),
                    "PROCESSING_ERROR",
                    e.getMessage()
                ));
                log.warn("텔레메트리 이벤트 저장 실패: eventId={}, error={}", 
                    eventItem.getEventId(), e.getMessage());
            }
        }

        return new TelemetryDtos.TelemetryEventResponse(received, accepted, rejected, errors);
    }

    /**
     * 보안 지표 조회
     */
    @Transactional(readOnly = true)
    public TelemetryDtos.SecurityMetricsResponse getSecurityMetrics(
        String period,
        String dept
    ) {
        Instant[] periodRange = calculatePeriodRange(period);
        Instant startDate = periodRange[0];
        Instant endDate = periodRange[1];

        // SECURITY 이벤트 조회
        List<TelemetryEvent> securityEvents = telemetryEventRepository
            .findByEventTypeAndPeriodAndDept("SECURITY", startDate, endDate, dept);

        // PII 차단 수집
        int piiBlockCount = 0;
        int externalDomainBlockCount = 0;

        for (TelemetryEvent event : securityEvents) {
            Map<String, Object> payload = (Map<String, Object>) event.getPayload();
            Boolean blocked = (Boolean) payload.get("blocked");
            String blockType = (String) payload.get("blockType");

            if (Boolean.TRUE.equals(blocked)) {
                if ("PII_BLOCK".equals(blockType)) {
                    piiBlockCount++;
                } else if ("EXTERNAL_DOMAIN_BLOCK".equals(blockType)) {
                    externalDomainBlockCount++;
                }
            }
        }

        // PII 추이 계산 (주간별)
        List<TelemetryDtos.PiiTrendItem> piiTrend = calculatePiiTrend(
            startDate, endDate, dept
        );

        return new TelemetryDtos.SecurityMetricsResponse(
            piiBlockCount,
            externalDomainBlockCount,
            piiTrend
        );
    }

    /**
     * 성능 지표 조회
     */
    @Transactional(readOnly = true)
    public TelemetryDtos.PerformanceMetricsResponse getPerformanceMetrics(
        String period,
        String dept
    ) {
        Instant[] periodRange = calculatePeriodRange(period);
        Instant startDate = periodRange[0];
        Instant endDate = periodRange[1];

        // CHAT_TURN 이벤트 조회
        List<TelemetryEvent> chatTurnEvents = telemetryEventRepository
            .findByEventTypeAndPeriodAndDept("CHAT_TURN", startDate, endDate, dept);

        // FEEDBACK 이벤트 조회
        List<TelemetryEvent> feedbackEvents = telemetryEventRepository
            .findByEventTypeAndPeriodAndDept("FEEDBACK", startDate, endDate, dept);

        // 불만족도 계산
        long dislikeCount = 0;
        long likeCount = 0;
        for (TelemetryEvent event : feedbackEvents) {
            Map<String, Object> payload = (Map<String, Object>) event.getPayload();
            String feedback = (String) payload.get("feedback");
            if ("dislike".equals(feedback)) {
                dislikeCount++;
            } else if ("like".equals(feedback)) {
                likeCount++;
            }
        }
        double dislikeRate = (likeCount + dislikeCount > 0) 
            ? (double) dislikeCount / (likeCount + dislikeCount) 
            : 0.0;

        // 재질문률 계산 (MVP: 동일 conversation, 최근 3턴 내, 같은 intentMain 반복)
        double repeatRate = calculateRepeatRate(chatTurnEvents);

        // OOS 카운트
        int oosCount = 0;
        for (TelemetryEvent event : chatTurnEvents) {
            Map<String, Object> payload = (Map<String, Object>) event.getPayload();
            Boolean oos = (Boolean) payload.get("oos");
            if (Boolean.TRUE.equals(oos)) {
                oosCount++;
            }
        }

        // 지연시간 히스토그램
        List<TelemetryDtos.LatencyHistogramItem> latencyHistogram = 
            calculateLatencyHistogram(chatTurnEvents);

        // 모델별 평균 지연시간
        List<TelemetryDtos.ModelLatencyItem> modelLatency = 
            calculateModelLatency(chatTurnEvents);

        return new TelemetryDtos.PerformanceMetricsResponse(
            dislikeRate,
            repeatRate,
            "MVP: same conversation, within last 3 turns, same intentMain repeated",
            oosCount,
            latencyHistogram,
            modelLatency
        );
    }

    /**
     * 기간 계산
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
     * PII 추이 계산 (주간별)
     */
    private List<TelemetryDtos.PiiTrendItem> calculatePiiTrend(
        Instant startDate, Instant endDate, String dept
    ) {
        // CHAT_TURN 이벤트에서 PII 감지 정보 추출
        List<TelemetryEvent> chatTurnEvents = telemetryEventRepository
            .findByEventTypeAndPeriodAndDept("CHAT_TURN", startDate, endDate, dept);

        // 주간별 집계를 위한 맵
        Map<String, List<Boolean>> weekInputPii = new java.util.HashMap<>();
        Map<String, List<Boolean>> weekOutputPii = new java.util.HashMap<>();

        for (TelemetryEvent event : chatTurnEvents) {
            Map<String, Object> payload = (Map<String, Object>) event.getPayload();
            Boolean piiInput = (Boolean) payload.get("piiDetectedInput");
            Boolean piiOutput = (Boolean) payload.get("piiDetectedOutput");

            // 주간 버킷 계산 (ISO 주 기준)
            String weekKey = getWeekKey(event.getOccurredAt());

            if (piiInput != null) {
                weekInputPii.computeIfAbsent(weekKey, k -> new ArrayList<>()).add(piiInput);
            }
            if (piiOutput != null) {
                weekOutputPii.computeIfAbsent(weekKey, k -> new ArrayList<>()).add(piiOutput);
            }
        }

        // 주간별 비율 계산
        List<TelemetryDtos.PiiTrendItem> result = new ArrayList<>();
        for (String weekKey : weekInputPii.keySet()) {
            List<Boolean> inputList = weekInputPii.get(weekKey);
            List<Boolean> outputList = weekOutputPii.getOrDefault(weekKey, new ArrayList<>());

            double inputDetectRate = inputList.stream()
                .mapToInt(b -> Boolean.TRUE.equals(b) ? 1 : 0)
                .sum() / (double) inputList.size();

            double outputDetectRate = outputList.isEmpty() ? 0.0 :
                outputList.stream()
                    .mapToInt(b -> Boolean.TRUE.equals(b) ? 1 : 0)
                    .sum() / (double) outputList.size();

            result.add(new TelemetryDtos.PiiTrendItem(weekKey, inputDetectRate, outputDetectRate));
        }

        // 주간 키로 정렬
        result.sort((a, b) -> a.getBucketStart().compareTo(b.getBucketStart()));

        return result;
    }

    /**
     * 주간 키 생성 (YYYY-MM-DD 형식, 주의 시작일)
     */
    private String getWeekKey(Instant instant) {
        LocalDate date = instant.atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate weekStart = date.minusDays(date.getDayOfWeek().getValue() - 1);
        return weekStart.toString();
    }

    /**
     * 재질문률 계산 (MVP: 동일 conversation, 최근 3턴 내, 같은 intentMain 반복)
     */
    private double calculateRepeatRate(List<TelemetryEvent> chatTurnEvents) {
        if (chatTurnEvents.isEmpty()) {
            return 0.0;
        }

        // conversationId별로 그룹화
        Map<String, List<TelemetryEvent>> byConversation = new java.util.HashMap<>();
        for (TelemetryEvent event : chatTurnEvents) {
            if (event.getConversationId() != null) {
                byConversation.computeIfAbsent(event.getConversationId(), k -> new ArrayList<>())
                    .add(event);
            }
        }

        int repeatCount = 0;
        int totalTurns = chatTurnEvents.size();

        for (List<TelemetryEvent> turns : byConversation.values()) {
            // turnId로 정렬
            turns.sort((a, b) -> {
                if (a.getTurnId() == null && b.getTurnId() == null) return 0;
                if (a.getTurnId() == null) return -1;
                if (b.getTurnId() == null) return 1;
                return a.getTurnId().compareTo(b.getTurnId());
            });

            // 최근 3턴 내에서 같은 intentMain 반복 체크
            for (int i = 0; i < turns.size(); i++) {
                TelemetryEvent current = turns.get(i);
                Map<String, Object> currentPayload = (Map<String, Object>) current.getPayload();
                String currentIntent = (String) currentPayload.get("intentMain");

                if (currentIntent == null) continue;

                // 최근 3턴 내 확인
                for (int j = Math.max(0, i - 3); j < i; j++) {
                    TelemetryEvent prev = turns.get(j);
                    Map<String, Object> prevPayload = (Map<String, Object>) prev.getPayload();
                    String prevIntent = (String) prevPayload.get("intentMain");

                    if (currentIntent.equals(prevIntent)) {
                        repeatCount++;
                        break; // 한 턴당 한 번만 카운트
                    }
                }
            }
        }

        return totalTurns > 0 ? (double) repeatCount / totalTurns : 0.0;
    }

    /**
     * 지연시간 히스토그램 계산
     */
    private List<TelemetryDtos.LatencyHistogramItem> calculateLatencyHistogram(
        List<TelemetryEvent> chatTurnEvents
    ) {
        long count0_500 = 0;
        long count500_1000 = 0;
        long count1000_2000 = 0;
        long count2000Plus = 0;

        for (TelemetryEvent event : chatTurnEvents) {
            Map<String, Object> payload = (Map<String, Object>) event.getPayload();
            Object latencyObj = payload.get("latencyMsTotal");
            if (latencyObj instanceof Number) {
                long latency = ((Number) latencyObj).longValue();
                if (latency < 500) {
                    count0_500++;
                } else if (latency < 1000) {
                    count500_1000++;
                } else if (latency < 2000) {
                    count1000_2000++;
                } else {
                    count2000Plus++;
                }
            }
        }

        List<TelemetryDtos.LatencyHistogramItem> histogram = new ArrayList<>();
        histogram.add(new TelemetryDtos.LatencyHistogramItem("0-500ms", count0_500));
        histogram.add(new TelemetryDtos.LatencyHistogramItem("0.5-1s", count500_1000));
        histogram.add(new TelemetryDtos.LatencyHistogramItem("1-2s", count1000_2000));
        histogram.add(new TelemetryDtos.LatencyHistogramItem("2s+", count2000Plus));

        return histogram;
    }

    /**
     * 모델별 평균 지연시간 계산
     */
    private List<TelemetryDtos.ModelLatencyItem> calculateModelLatency(
        List<TelemetryEvent> chatTurnEvents
    ) {
        Map<String, List<Long>> modelLatencies = new java.util.HashMap<>();

        for (TelemetryEvent event : chatTurnEvents) {
            Map<String, Object> payload = (Map<String, Object>) event.getPayload();
            String model = (String) payload.get("model");
            Object latencyObj = payload.get("latencyMsLlm");
            
            if (model != null && latencyObj instanceof Number) {
                modelLatencies.computeIfAbsent(model, k -> new ArrayList<>())
                    .add(((Number) latencyObj).longValue());
            }
        }

        List<TelemetryDtos.ModelLatencyItem> result = new ArrayList<>();
        for (Map.Entry<String, List<Long>> entry : modelLatencies.entrySet()) {
            double avg = entry.getValue().stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0);
            result.add(new TelemetryDtos.ModelLatencyItem(entry.getKey(), avg));
        }

        return result;
    }
}

