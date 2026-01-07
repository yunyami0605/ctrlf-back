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
                log.info("[AI 로그 Bulk 저장] Bulk insert 완료: saved={}", saved);
            } catch (Exception e) {
                log.error("[AI 로그 Bulk 저장] Bulk insert 실패: count={}, error={}", 
                    logsToSave.size(), e.getMessage(), e);
                // 개별 저장 시도
                for (AiLog aiLogItem : logsToSave) {
                    try {
                        aiLogRepository.save(aiLogItem);
                        saved++;
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
     */
    @Transactional(readOnly = true)
    public AiLogDtos.PageResponse<AiLogDtos.LogListItem> getLogs(
        String period,
        String startDateStr,
        String endDateStr,
        String department,
        String domain,
        String route,
        String model,
        Boolean onlyError,
        Boolean hasPiiOnly,
        Integer page,
        Integer size,
        String sort
    ) {
        try {
            log.info("로그 조회 요청: period={}, startDate={}, endDate={}, department={}, domain={}, route={}, model={}, onlyError={}, hasPiiOnly={}, page={}, size={}, sort={}",
                period, startDateStr, endDateStr, department, domain, route, model, onlyError, hasPiiOnly, page, size, sort);

            // 기간 계산: startDate/endDate가 있으면 우선 사용, 없으면 period 사용
            Instant startDate;
            Instant endDate;
            
            if (startDateStr != null && !startDateStr.isBlank()) {
                // startDate 파싱
                try {
                    startDate = Instant.parse(startDateStr);
                    // endDate가 있으면 사용, 없으면 현재 시각 사용
                    if (endDateStr != null && !endDateStr.isBlank()) {
                        endDate = Instant.parse(endDateStr);
                    } else {
                        endDate = Instant.now();  // 최신 데이터까지 포함
                    }
                    log.debug("날짜 파싱 성공: startDate={}, endDate={}", startDate, endDate);
                } catch (Exception e) {
                    log.warn("날짜 파싱 실패: startDate={}, endDate={}, error={}", 
                        startDateStr, endDateStr, e.getMessage(), e);
                    // 파싱 실패 시 period 사용
                    Instant[] periodRange = calculatePeriodRange(period);
                    startDate = periodRange[0];
                    endDate = periodRange[1];
                }
            } else {
                // period 사용
                Instant[] periodRange = calculatePeriodRange(period);
                startDate = periodRange[0];
                endDate = periodRange[1];
                log.info("period 사용: startDate={}, endDate={}", startDate, endDate);
            }

            // 페이징 및 정렬 설정
            int pageNumber = (page != null && page >= 0) ? page : 0;
            int pageSize = (size != null && size > 0) ? Math.min(size, 100) : 20;
            
            Pageable pageable;
            if (sort != null && !sort.isBlank()) {
                // sort 파라미터 파싱 (예: "createdAt,desc" 또는 "createdAt,asc")
                try {
                    String[] sortParts = sort.split(",");
                    if (sortParts.length == 0) {
                        throw new IllegalArgumentException("정렬 파라미터 형식이 잘못되었습니다: " + sort);
                    }
                    String sortField = sortParts[0].trim();
                    Sort.Direction direction = sortParts.length > 1 && 
                        "desc".equalsIgnoreCase(sortParts[1].trim()) 
                        ? Sort.Direction.DESC 
                        : Sort.Direction.ASC;
                    
                    // 필드명 검증 (보안을 위해 허용된 필드만)
                    if (isValidSortField(sortField)) {
                        pageable = PageRequest.of(pageNumber, pageSize, Sort.by(direction, sortField));
                        log.debug("정렬 적용: field={}, direction={}", sortField, direction);
                    } else {
                        log.warn("잘못된 정렬 필드: {}, 기본 정렬 사용", sortField);
                        pageable = PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
                    }
                } catch (Exception e) {
                    log.warn("정렬 파싱 실패: sort={}, error={}", sort, e.getMessage(), e);
                    pageable = PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
                }
            } else {
                // 기본 정렬: createdAt 내림차순
                pageable = PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
            }

            // Specification을 사용하여 동적 쿼리 생성
            Specification<AiLog> spec = buildSpecification(
                startDate, endDate, department, domain, route, model, onlyError, hasPiiOnly
            );
            
            log.info("DB 조회 시작: startDate={}, endDate={}, department={}, domain={}, route={}, model={}, onlyError={}, hasPiiOnly={}, pageNumber={}, pageSize={}", 
                startDate, endDate, department, domain, route, model, onlyError, hasPiiOnly, pageNumber, pageSize);
            
            Page<AiLog> logPage;
            try {
                logPage = aiLogRepository.findAll(spec, pageable);
                log.info("DB 조회 완료: totalElements={}, totalPages={}, contentSize={}, pageNumber={}, pageSize={}", 
                    logPage.getTotalElements(), logPage.getTotalPages(), logPage.getContent().size(), logPage.getNumber(), logPage.getSize());
            } catch (Exception e) {
                log.error("DB 조회 중 예외 발생: startDate={}, endDate={}, error={}", 
                    startDate, endDate, e.getMessage(), e);
                throw e;
            }

            // DTO 변환
            List<AiLogDtos.LogListItem> content = logPage.getContent().stream()
                .map(this::convertToLogListItem)
                .toList();

            // 최신 데이터 확인을 위한 디버깅 로그
            if (!content.isEmpty()) {
                log.info("조회된 로그 중 가장 최신: createdAt={}, userId={}, domain={}, route={}", 
                    content.get(0).getCreatedAt(), content.get(0).getUserId(), 
                    content.get(0).getDomain(), content.get(0).getRoute());
            }
            
            // DB에 실제로 최신 데이터가 있는지 확인 (최근 1시간 내 데이터)
            long recentCount = aiLogRepository.findAll((root, query, cb) -> {
                Instant oneHourAgo = Instant.now().minusSeconds(3600);
                return cb.greaterThanOrEqualTo(root.get("createdAt"), oneHourAgo);
            }).size();
            log.info("최근 1시간 내 DB에 저장된 로그 개수: {}", recentCount);

            log.info("로그 조회 성공: totalElements={}, contentSize={}, startDate={}, endDate={}", 
                logPage.getTotalElements(), content.size(), startDate, endDate);

            return new AiLogDtos.PageResponse<>(
                content,
                logPage.getTotalElements(),
                logPage.getTotalPages(),
                logPage.getNumber(),
                logPage.getSize()
            );
        } catch (Exception e) {
            log.error("로그 조회 중 오류 발생", e);
            throw new RuntimeException("로그 조회 실패: " + e.getMessage(), e);
        }
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

