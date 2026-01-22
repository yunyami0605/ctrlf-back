package com.ctrlf.infra.ailog.service;

import com.ctrlf.infra.ailog.dto.AiLogDtos;
import com.ctrlf.infra.ailog.entity.AiLog;
import com.ctrlf.infra.ailog.repository.AiLogRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 로그 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AiLogService {

    private final AiLogRepository aiLogRepository;
    private final com.ctrlf.infra.elasticsearch.service.ChatLogElasticsearchService chatLogElasticsearchService;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * AI 로그 Bulk 저장
     * 
     * <p>AI 서버의 LogSyncService에서 주기적으로 전송하는 로그를 저장합니다.</p>
     * <p>중복 방지: traceId + conversationId + turnId 조합으로 중복 체크</p>
     * <p>성능 최적화: Bulk insert 사용</p>
     */
    public AiLogDtos.BulkResponse saveBulkLogs(AiLogDtos.BulkRequest request) {
        int received = request.getLogs() != null ? request.getLogs().size() : 0;
        int saved = 0;
        int failed = 0;
        int skipped = 0;  // 중복으로 건너뛴 개수
        List<AiLogDtos.ErrorItem> errors = new ArrayList<>();

        if (request.getLogs() == null || request.getLogs().isEmpty()) {
            log.warn("[AI 로그 Bulk 저장] 빈 요청 수신: logs가 null이거나 비어있음");
            return new AiLogDtos.BulkResponse(0, 0, 0, errors);
        }

        log.info("[AI 로그 Bulk 저장] 시작: received={}", received);

        // 중복 체크를 위한 Set (traceId + conversationId + turnId 조합)
        java.util.Set<String> duplicateKeys = new java.util.HashSet<>();
        List<AiLog> logsToSave = new ArrayList<>();
        // Elasticsearch 저장을 위한 원본 LogItem 매핑 (traceId+conversationId+turnId -> LogItem)
        java.util.Map<String, AiLogDtos.LogItem> logItemMap = new java.util.HashMap<>();
        Instant receivedAt = Instant.now();

        for (int i = 0; i < request.getLogs().size(); i++) {
            AiLogDtos.LogItem logItem = request.getLogs().get(i);
            try {
                // 로그 항목 상세 정보 로깅 (첫 번째 항목만)
                if (i == 0) {
                    log.debug("[AI 로그 Bulk 저장] 첫 번째 로그 항목: createdAt={}, userId={}, domain={}, route={}, traceId={}, conversationId={}, turnId={}",
                        logItem.getCreatedAt(), logItem.getUserId(), logItem.getDomain(), 
                        logItem.getRoute(), logItem.getTraceId(), logItem.getConversationId(), logItem.getTurnId());
                }

                // 필수 필드 검증
                if (logItem.getCreatedAt() == null || logItem.getUserId() == null) {
                    failed++;
                    errors.add(new AiLogDtos.ErrorItem(
                        i,
                        "VALIDATION_ERROR",
                        "createdAt 또는 userId가 null입니다."
                    ));
                    log.warn("[AI 로그 Bulk 저장] 필수 필드 누락: index={}, createdAt={}, userId={}", 
                        i, logItem.getCreatedAt(), logItem.getUserId());
                    continue;
                }

                // 중복 체크: traceId, conversationId, turnId 조합
                String duplicateKey = buildDuplicateKey(
                    logItem.getTraceId(), 
                    logItem.getConversationId(), 
                    logItem.getTurnId()
                );
                
                if (duplicateKey != null) {
                    if (duplicateKeys.contains(duplicateKey)) {
                        // 같은 요청 내에서 중복
                        skipped++;
                        log.debug("[AI 로그 Bulk 저장] 요청 내 중복 건너뜀: index={}, key={}", i, duplicateKey);
                        continue;
                    }
                    
                    // DB에서 중복 체크
                    Optional<AiLog> existing = aiLogRepository.findByTraceIdAndConversationIdAndTurnId(
                        logItem.getTraceId(),
                        logItem.getConversationId(),
                        logItem.getTurnId()
                    );
                    
                    if (existing.isPresent()) {
                        skipped++;
                        duplicateKeys.add(duplicateKey);
                        log.debug("[AI 로그 Bulk 저장] DB 중복 건너뜀: index={}, key={}, existingId={}", 
                            i, duplicateKey, existing.get().getId());
                        continue;
                    }
                    
                    duplicateKeys.add(duplicateKey);
                }

                // AiLog 엔티티 생성
                AiLog aiLog = new AiLog();
                aiLog.setCreatedAt(logItem.getCreatedAt());
                aiLog.setUserId(logItem.getUserId());
                aiLog.setUserRole(logItem.getUserRole());
                aiLog.setDepartment(logItem.getDepartment());
                aiLog.setDomain(logItem.getDomain());
                aiLog.setRoute(logItem.getRoute());
                aiLog.setModelName(logItem.getModelName());
                aiLog.setHasPiiInput(logItem.getHasPiiInput());
                aiLog.setHasPiiOutput(logItem.getHasPiiOutput());
                aiLog.setRagUsed(logItem.getRagUsed());
                aiLog.setRagSourceCount(logItem.getRagSourceCount());
                aiLog.setLatencyMsTotal(logItem.getLatencyMsTotal());
                aiLog.setErrorCode(logItem.getErrorCode());
                aiLog.setTraceId(logItem.getTraceId());
                aiLog.setConversationId(logItem.getConversationId());
                aiLog.setTurnId(logItem.getTurnId());
                aiLog.setReceivedAt(receivedAt);

                logsToSave.add(aiLog);
                
                // Elasticsearch 저장을 위한 원본 LogItem 매핑 (traceId+conversationId+turnId -> LogItem)
                String mapKey = buildDuplicateKey(
                    logItem.getTraceId(),
                    logItem.getConversationId(),
                    logItem.getTurnId()
                );
                if (mapKey == null) {
                    // 키를 생성할 수 없으면 임시로 UUID 사용 (실제로는 거의 발생하지 않음)
                    mapKey = UUID.randomUUID().toString();
                }
                logItemMap.put(mapKey, logItem);

            } catch (Exception e) {
                failed++;
                errors.add(new AiLogDtos.ErrorItem(
                    i,
                    "PROCESSING_ERROR",
                    e.getMessage()
                ));
                log.error("[AI 로그 Bulk 저장] 처리 실패: index={}, createdAt={}, userId={}, domain={}, route={}, traceId={}, error={}",
                    i, logItem.getCreatedAt(), logItem.getUserId(), logItem.getDomain(), 
                    logItem.getRoute(), logItem.getTraceId(), e.getMessage(), e);
            }
        }

        // Bulk insert (성능 최적화)
        if (!logsToSave.isEmpty()) {
            try {
                aiLogRepository.saveAll(logsToSave);
                saved = logsToSave.size();
                log.info("[AI 로그 Bulk 저장] PostgreSQL Bulk insert 완료: saved={}", saved);
                
                // Elasticsearch에도 저장 (실패해도 PostgreSQL 저장은 성공으로 처리)
                int esSaved = 0;
                for (AiLog aiLog : logsToSave) {
                    try {
                        // 원본 LogItem 찾기 (traceId+conversationId+turnId로 매칭)
                        String mapKey = buildDuplicateKey(
                            aiLog.getTraceId(),
                            aiLog.getConversationId(),
                            aiLog.getTurnId()
                        );
                        
                        if (mapKey != null && logItemMap.containsKey(mapKey)) {
                            AiLogDtos.LogItem logItem = logItemMap.get(mapKey);
                            chatLogElasticsearchService.saveChatLog(
                                logItem,
                                aiLog.getId().toString()
                            );
                            esSaved++;
                        } else {
                            log.warn("[AI 로그 Bulk 저장] Elasticsearch 저장: 원본 LogItem을 찾을 수 없음: id={}, traceId={}, conversationId={}, turnId={}",
                                aiLog.getId(), aiLog.getTraceId(), aiLog.getConversationId(), aiLog.getTurnId());
                        }
                    } catch (Exception e) {
                        // Elasticsearch 저장 실패는 로그만 남기고 계속 진행
                        log.warn("[AI 로그 Bulk 저장] Elasticsearch 저장 실패: id={}, traceId={}, error={}",
                            aiLog.getId(), aiLog.getTraceId(), e.getMessage());
                    }
                }
                log.info("[AI 로그 Bulk 저장] Elasticsearch 저장 완료: esSaved={}, total={}", esSaved, saved);
            } catch (Exception e) {
                log.error("[AI 로그 Bulk 저장] Bulk insert 실패: count={}, error={}", 
                    logsToSave.size(), e.getMessage(), e);
                // 개별 저장 시도
                for (AiLog aiLogItem : logsToSave) {
                    try {
                        aiLogRepository.save(aiLogItem);
                        saved++;
                        
                        // Elasticsearch에도 저장 시도
                        try {
                            String mapKey = buildDuplicateKey(
                                aiLogItem.getTraceId(),
                                aiLogItem.getConversationId(),
                                aiLogItem.getTurnId()
                            );
                            
                            if (mapKey != null && logItemMap.containsKey(mapKey)) {
                                AiLogDtos.LogItem logItem = logItemMap.get(mapKey);
                                chatLogElasticsearchService.saveChatLog(
                                    logItem,
                                    aiLogItem.getId().toString()
                                );
                            }
                        } catch (Exception ex) {
                            log.warn("[AI 로그 Bulk 저장] Elasticsearch 저장 실패: id={}, error={}",
                                aiLogItem.getId(), ex.getMessage());
                        }
                    } catch (Exception ex) {
                        failed++;
                        log.error("[AI 로그 Bulk 저장] 개별 저장 실패: traceId={}, error={}", 
                            aiLogItem.getTraceId(), ex.getMessage());
                    }
                }
            }
        }

        log.info("[AI 로그 Bulk 저장] 완료: received={}, saved={}, skipped={}, failed={}", 
            received, saved, skipped, failed);

        if (failed > 0) {
            log.warn("[AI 로그 Bulk 저장] 일부 실패: errors={}", errors);
        }
        
        if (skipped > 0) {
            log.info("[AI 로그 Bulk 저장] 중복 건너뜀: skipped={}", skipped);
        }

        return new AiLogDtos.BulkResponse(received, saved, failed, errors);
    }

    /**
     * 중복 체크를 위한 키 생성
     * 
     * @param traceId 트레이스 ID
     * @param conversationId 대화 ID
     * @param turnId 턴 ID
     * @return 중복 체크 키 (null이면 중복 체크 불가)
     */
    private String buildDuplicateKey(String traceId, String conversationId, Integer turnId) {
        // traceId, conversationId, turnId가 모두 있어야 중복 체크 가능
        if (traceId != null && conversationId != null && turnId != null) {
            return traceId + "|" + conversationId + "|" + turnId;
        }
        return null;
    }

    /**
     * 관리자 대시보드 로그 목록 조회
     * 
     * <p>Elasticsearch chat_log 인덱스에서 실시간 채팅 로그를 조회합니다.</p>
     * <p>백엔드에서 채팅 메시지 저장 시 자동으로 저장된 로그를 조회합니다.</p>
     */
    @Transactional(readOnly = true)
    public AiLogDtos.PageResponse<AiLogDtos.LogListItem> getLogs(AiLogDtos.LogListRequest request) {
        log.info("[AI 로그 조회] Elasticsearch에서 조회: period={}, startDate={}, endDate={}, department={}, domain={}, route={}, model={}, onlyError={}, hasPiiOnly={}, page={}, size={}, sort={}",
            request.getPeriod(), request.getStartDate(), request.getEndDate(), request.getDepartment(), 
            request.getDomain(), request.getRoute(), request.getModel(), request.getOnlyError(), 
            request.getHasPiiOnly(), request.getPage(), request.getSize(), request.getSort());

        // Elasticsearch에서 조회 (실시간 채팅 로그)
        return chatLogElasticsearchService.getLogs(request);
    }

    /**
     * 엔티티를 DTO로 변환
     */
    private AiLogDtos.LogListItem convertToLogListItem(AiLog aiLog) {
        String createdAtStr = aiLog.getCreatedAt() != null
            ? aiLog.getCreatedAt().atZone(ZoneId.systemDefault())
                .format(DATE_TIME_FORMATTER)
            : null;

        return new AiLogDtos.LogListItem(
            aiLog.getId(),
            createdAtStr,
            aiLog.getUserId(),
            aiLog.getUserRole(),
            aiLog.getDepartment(),
            aiLog.getDomain(),
            aiLog.getRoute(),
            aiLog.getModelName(),
            aiLog.getHasPiiInput(),
            aiLog.getHasPiiOutput(),
            aiLog.getRagUsed(),
            aiLog.getRagSourceCount(),
            aiLog.getLatencyMsTotal(),
            aiLog.getErrorCode()
        );
    }

    /**
     * 기간 계산
     */
    private Instant[] calculatePeriodRange(String period) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate;

        if (period == null || period.isBlank()) {
            period = "30";
        }

        switch (period) {
            case "7":
                startDate = endDate.minusDays(7);
                break;
            case "30":
                startDate = endDate.minusDays(30);
                break;
            case "90":
                startDate = endDate.minusDays(90);
                break;
            default:
                startDate = endDate.minusDays(30);
        }

        // endDate를 현재 시각으로 설정하여 최신 데이터까지 포함
        // startDate는 해당 날짜의 자정부터 시작
        return new Instant[] {
            startDate.atStartOfDay(ZoneId.systemDefault()).toInstant(),
            Instant.now()  // 현재 시각까지 포함
        };
    }

    /**
     * 정렬 필드 검증
     */
    private boolean isValidSortField(String field) {
        // 허용된 정렬 필드만 허용 (보안)
        return field != null && (
            field.equals("createdAt") ||
            field.equals("receivedAt") ||
            field.equals("latencyMsTotal") ||
            field.equals("userId") ||
            field.equals("department") ||
            field.equals("domain") ||
            field.equals("route")
        );
    }

    /**
     * 동적 쿼리를 위한 Specification 생성
     */
    private Specification<AiLog> buildSpecification(
        Instant startDate,
        Instant endDate,
        String department,
        String domain,
        String route,
        String model,
        Boolean onlyError,
        Boolean hasPiiOnly
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 날짜 범위 필터
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }
            if (endDate != null) {
                // endDate는 현재 시각이므로 lessThanOrEqualTo를 사용하여 현재 시각까지 포함
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }

            // 부서 필터
            if (department != null && !department.isBlank()) {
                predicates.add(cb.equal(root.get("department"), department));
            }

            // 도메인 필터
            if (domain != null && !domain.isBlank()) {
                predicates.add(cb.equal(root.get("domain"), domain));
            }

            // 라우트 필터
            if (route != null && !route.isBlank()) {
                predicates.add(cb.equal(root.get("route"), route));
            }

            // 모델 필터
            if (model != null && !model.isBlank()) {
                predicates.add(cb.equal(root.get("modelName"), model));
            }

            // 에러만 보기 필터
            if (onlyError != null && onlyError) {
                predicates.add(cb.isNotNull(root.get("errorCode")));
            }

            // PII 포함만 보기 필터
            if (hasPiiOnly != null && hasPiiOnly) {
                predicates.add(cb.or(
                    cb.isTrue(root.get("hasPiiInput")),
                    cb.isTrue(root.get("hasPiiOutput"))
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

