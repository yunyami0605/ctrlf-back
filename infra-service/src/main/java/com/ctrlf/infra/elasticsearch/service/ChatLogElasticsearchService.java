package com.ctrlf.infra.elasticsearch.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.ctrlf.infra.ailog.dto.AiLogDtos;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Elasticsearch 채팅 로그 저장 및 조회 서비스
 * 
 * <p>AI 서버에서 전송된 로그를 Elasticsearch chat_log 인덱스에 저장하고, 관리자 대시보드에서 조회합니다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatLogElasticsearchService {

    private final ElasticsearchClient elasticsearchClient;

    @Value("${app.elasticsearch.chat-log-index:chat_log}")
    private String chatLogIndex;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    /**
     * AI 로그를 Elasticsearch에 저장
     *
     * <p>AI 서버에서 전송된 로그를 Elasticsearch chat_log 인덱스에 저장합니다.</p>
     * <p>실패해도 예외를 던지지 않아 PostgreSQL 저장은 계속 진행됩니다.</p>
     *
     * @param logItem AI 로그 항목
     * @param id Elasticsearch 문서 ID (UUID 문자열)
     */
    public void saveChatLog(AiLogDtos.LogItem logItem, String id) {
        try {
            Map<String, Object> logData = new HashMap<>();
            
            // 기본 필드
            logData.put("id", id);
            logData.put("createdAt", logItem.getCreatedAt().toString());
            logData.put("userId", logItem.getUserId());
            logData.put("userRole", logItem.getUserRole());
            logData.put("domain", logItem.getDomain() != null ? logItem.getDomain() : "ETC");
            logData.put("department", logItem.getDepartment());
            logData.put("route", logItem.getRoute());
            logData.put("modelName", logItem.getModelName());
            
            // 질문/답변 필드 (AI 서버에서 전송하는 경우)
            // AI 서버가 보내는 로그에 question, answer가 포함되어 있으면 사용
            // 없으면 null로 저장 (chat-service에서 저장한 로그와 병합 가능)
            logData.put("question", null);  // AI 서버 로그에는 질문/답변 원문이 없을 수 있음
            logData.put("answer", null);
            logData.put("role", "assistant");  // AI 서버 로그는 주로 assistant 응답
            
            // PII 필드
            logData.put("hasPiiInput", logItem.getHasPiiInput() != null ? logItem.getHasPiiInput() : false);
            logData.put("hasPiiOutput", logItem.getHasPiiOutput() != null ? logItem.getHasPiiOutput() : false);
            
            // RAG 필드
            logData.put("ragUsed", logItem.getRagUsed() != null ? logItem.getRagUsed() : false);
            logData.put("ragSourceCount", logItem.getRagSourceCount());
            
            // 성능/에러 필드
            logData.put("latencyMsTotal", logItem.getLatencyMsTotal());
            logData.put("errorCode", logItem.getErrorCode());
            
            // 추적 필드
            logData.put("traceId", logItem.getTraceId());
            logData.put("conversationId", logItem.getConversationId());
            logData.put("turnId", logItem.getTurnId());
            
            // Elasticsearch에 저장
            IndexRequest<Map<String, Object>> request = IndexRequest.of(i -> i
                .index(chatLogIndex)
                .id(id)
                .document(logData)
            );
            
            elasticsearchClient.index(request);
            
            log.debug("[Elasticsearch AI 로그 저장] 성공: id={}, createdAt={}, userId={}, domain={}",
                id, logItem.getCreatedAt(), logItem.getUserId(), logItem.getDomain());
                
        } catch (Exception e) {
            log.error("[Elasticsearch AI 로그 저장] 실패: id={}, createdAt={}, userId={}, error={}",
                id, logItem.getCreatedAt(), logItem.getUserId(), e.getMessage(), e);
            // Elasticsearch 저장 실패해도 PostgreSQL 저장은 계속 진행되도록 예외를 던지지 않음
        }
    }

    /**
     * 관리자 대시보드 로그 목록 조회
     *
     * <p>Elasticsearch chat_log 인덱스에서 채팅 로그를 조회합니다.</p>
     *
     * @param period 기간 (7 | 30 | 90)
     * @param startDateStr 시작 날짜 (ISO 8601)
     * @param endDateStr 종료 날짜 (ISO 8601)
     * @param department 부서명 필터
     * @param domain 도메인 필터
     * @param route 라우트 필터
     * @param model 모델명 필터
     * @param onlyError 에러만 보기
     * @param hasPiiOnly PII 포함만 보기
     * @param page 페이지 번호
     * @param size 페이지 크기
     * @param sort 정렬 (예: createdAt,desc)
     * @return AI 로그 페이지 응답
     */
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
            log.info("[Elasticsearch 로그 조회] 요청: period={}, startDate={}, endDate={}, department={}, domain={}, route={}, model={}, onlyError={}, hasPiiOnly={}, page={}, size={}, sort={}",
                period, startDateStr, endDateStr, department, domain, route, model, onlyError, hasPiiOnly, page, size, sort);

            // 기간 계산
            Instant[] periodRange = calculatePeriodRange(period, startDateStr, endDateStr);
            Instant startDate = periodRange[0];
            Instant endDate = periodRange[1];

            // 페이징 설정
            int pageNumber = (page != null && page >= 0) ? page : 0;
            int pageSize = (size != null && size > 0) ? Math.min(size, 100) : 20;
            int from = pageNumber * pageSize;

            // 정렬 설정 (final 변수로 선언)
            final String finalSortField;
            final String finalSortOrder;
            if (sort != null && !sort.isBlank()) {
                String[] sortParts = sort.split(",");
                if (sortParts.length > 0) {
                    finalSortField = sortParts[0].trim();
                } else {
                    finalSortField = "createdAt";
                }
                if (sortParts.length > 1) {
                    finalSortOrder = sortParts[1].trim();
                } else {
                    finalSortOrder = "desc";
                }
            } else {
                finalSortField = "createdAt";
                finalSortOrder = "desc";
            }

            // 쿼리 빌더 생성
            BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();

            // 날짜 범위 필터 (ISO 8601 형식 문자열)
            String startDateIso = startDate.toString();
            String endDateIso = endDate.toString();
            boolQueryBuilder.must(Query.of(q -> q
                .range(r -> r
                    .field("createdAt")
                    .gte(JsonData.of(startDateIso))
                    .lte(JsonData.of(endDateIso))
                )
            ));

            // 부서 필터
            if (department != null && !department.isBlank()) {
                boolQueryBuilder.must(Query.of(q -> q
                    .term(t -> t.field("department").value(department))
                ));
            }

            // 도메인 필터
            if (domain != null && !domain.isBlank()) {
                boolQueryBuilder.must(Query.of(q -> q
                    .term(t -> t.field("domain").value(domain))
                ));
            }

            // 라우트 필터
            if (route != null && !route.isBlank()) {
                boolQueryBuilder.must(Query.of(q -> q
                    .term(t -> t.field("route").value(route))
                ));
            }

            // 모델 필터 (modelName 필드 사용)
            if (model != null && !model.isBlank()) {
                boolQueryBuilder.must(Query.of(q -> q
                    .term(t -> t.field("modelName").value(model))
                ));
            }

            // 에러만 보기 필터
            if (onlyError != null && onlyError) {
                boolQueryBuilder.must(Query.of(q -> q
                    .exists(e -> e.field("errorCode"))
                ));
            }

            // PII 포함만 보기 필터
            if (hasPiiOnly != null && hasPiiOnly) {
                boolQueryBuilder.must(Query.of(q -> q
                    .bool(b -> b
                        .should(s -> s.term(t -> t.field("hasPiiInput").value(true)))
                        .should(s -> s.term(t -> t.field("hasPiiOutput").value(true)))
                    )
                ));
            }

            BoolQuery boolQuery = boolQueryBuilder.build();

            // 검색 요청 생성
            SearchRequest searchRequest = SearchRequest.of(s -> s
                .index(chatLogIndex)
                .query(Query.of(q -> q.bool(boolQuery)))
                .from(from)
                .size(pageSize)
                .sort(so -> so
                    .field(f -> f
                        .field(finalSortField)
                        .order("desc".equalsIgnoreCase(finalSortOrder)
                            ? SortOrder.Desc
                            : SortOrder.Asc)
                    )
                )
            );

            log.info("[Elasticsearch 로그 조회] 검색 시작: index={}, from={}, size={}", chatLogIndex, from, pageSize);

            // 검색 실행
            SearchResponse<Map> response = elasticsearchClient.search(searchRequest, Map.class);

            long totalHits = response.hits().total() != null ? response.hits().total().value() : 0;
            int hitsSize = response.hits().hits().size();
            
            log.info("[Elasticsearch 로그 조회] 검색 완료: totalHits={}, hits={}", totalHits, hitsSize);

            // 샘플 데이터 로깅 (디버깅용, 최대 3개)
            if (hitsSize > 0) {
                log.info("[Elasticsearch 로그 조회] ✅ 샘플 데이터 (최대 3개):");
                for (int i = 0; i < Math.min(3, hitsSize); i++) {
                    Hit<Map> hit = response.hits().hits().get(i);
                    Map<String, Object> source = hit.source();
                    if (source != null) {
                        Object createdAtField = source.get("createdAt");
                        if (createdAtField == null) {
                            createdAtField = source.get("created_at");
                        }
                        log.info("[Elasticsearch 로그 조회] 샘플[{}]: id={}, createdAt={}, role={}, domain={}, userId={}, route={}",
                            i, hit.id(), 
                            createdAtField,
                            source.get("role"),
                            source.get("domain"),
                            source.get("userId"),
                            source.get("route"));
                    }
                }
            }

            // 결과 변환
            List<AiLogDtos.LogListItem> items = new ArrayList<>();
            int conversionSuccess = 0;
            int conversionFailed = 0;
            for (Hit<Map> hit : response.hits().hits()) {
                try {
                    Map<String, Object> source = hit.source();
                    if (source == null) {
                        conversionFailed++;
                        log.warn("[Elasticsearch 로그 조회] source가 null: id={}", hit.id());
                        continue;
                    }

                    AiLogDtos.LogListItem item = convertToLogListItem(hit.id(), source);
                    if (item != null) {
                        items.add(item);
                        conversionSuccess++;
                    } else {
                        conversionFailed++;
                        log.warn("[Elasticsearch 로그 조회] 변환 결과가 null: id={}", hit.id());
                    }
                } catch (Exception e) {
                    conversionFailed++;
                    log.warn("[Elasticsearch 로그 조회] 항목 변환 실패: id={}, error={}", hit.id(), e.getMessage(), e);
                }
            }
            
            log.info("[Elasticsearch 로그 조회] 변환 완료: totalHits={}, items={}, conversionSuccess={}, conversionFailed={}", 
                totalHits, items.size(), conversionSuccess, conversionFailed);

            // 총 개수 및 페이지 수 계산
            int totalPages = (int) Math.ceil((double) totalHits / pageSize);

            log.info("[Elasticsearch 로그 조회] 변환 완료: items={}, totalHits={}, totalPages={}", items.size(), totalHits, totalPages);

            return new AiLogDtos.PageResponse<>(
                items,
                totalHits,
                totalPages,
                pageNumber,
                pageSize
            );

        } catch (ElasticsearchException e) {
            if (e.getMessage() != null && e.getMessage().contains("index_not_found_exception")) {
                log.warn("[Elasticsearch 로그 조회] 인덱스가 없습니다: index={}, message={}. 빈 결과를 반환합니다.",
                    chatLogIndex, e.getMessage());
                return new AiLogDtos.PageResponse<>(
                    new ArrayList<>(),
                    0L,
                    0,
                    0,
                    20
                );
            } else {
                log.error("[Elasticsearch 로그 조회] Elasticsearch 오류 발생: index={}, error={}",
                    chatLogIndex, e.getMessage(), e);
                throw new RuntimeException("로그 조회 실패: " + e.getMessage(), e);
            }
        } catch (Exception e) {
            log.error("[Elasticsearch 로그 조회] 오류 발생: index={}, error={}",
                chatLogIndex, e.getMessage(), e);
            throw new RuntimeException("로그 조회 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 기간 계산 헬퍼 메서드
     */
    private Instant[] calculatePeriodRange(String period, String startDateStr, String endDateStr) {
        Instant startDate;
        Instant endDate = Instant.now();

        if (startDateStr != null && !startDateStr.isBlank()) {
            try {
                startDate = Instant.parse(startDateStr);
                if (endDateStr != null && !endDateStr.isBlank()) {
                    endDate = Instant.parse(endDateStr);
                }
                return new Instant[]{startDate, endDate};
            } catch (Exception e) {
                log.warn("[Elasticsearch 로그 조회] 날짜 파싱 실패: startDate={}, endDate={}, error={}",
                    startDateStr, endDateStr, e.getMessage());
            }
        }

        // period 사용
        int daysBack;
        if (period != null) {
            try {
                daysBack = Integer.parseInt(period);
            } catch (NumberFormatException e) {
                daysBack = 30; // 기본값
            }
        } else {
            daysBack = 30; // 기본값
        }

        startDate = Instant.now().minusSeconds(daysBack * 24L * 60L * 60L);
        return new Instant[]{startDate, endDate};
    }

    /**
     * Elasticsearch 문서를 LogListItem으로 변환
     */
    @SuppressWarnings("unchecked")
    private AiLogDtos.LogListItem convertToLogListItem(String id, Map<String, Object> source) {
        try {
            UUID itemId = null;
            try {
                itemId = UUID.fromString(id);
            } catch (IllegalArgumentException e) {
                log.debug("[Elasticsearch 로그 조회] ID를 UUID로 변환 실패: id={}", id);
            }

            // createdAt 변환 (camelCase 우선, snake_case 대체)
            String createdAtStr = null;
            Object createdAtObj = source.get("createdAt");
            if (createdAtObj == null) {
                createdAtObj = source.get("created_at");
            }
            if (createdAtObj != null) {
                if (createdAtObj instanceof String) {
                    String createdAtValue = (String) createdAtObj;
                    if (!createdAtValue.isBlank()) {
                        try {
                            Instant instant = Instant.parse(createdAtValue);
                            createdAtStr = DATE_TIME_FORMATTER.format(instant);
                        } catch (Exception e) {
                            log.warn("[Elasticsearch 로그 조회] createdAt 파싱 실패: createdAt={}, error={}", createdAtValue, e.getMessage());
                        }
                    }
                } else if (createdAtObj instanceof Number) {
                    try {
                        Instant instant = Instant.ofEpochMilli(((Number) createdAtObj).longValue());
                        createdAtStr = DATE_TIME_FORMATTER.format(instant);
                    } catch (Exception e) {
                        log.warn("[Elasticsearch 로그 조회] createdAt 변환 실패: createdAt={}, error={}", createdAtObj, e.getMessage());
                    }
                }
            }
            
            // createdAt이 null인 경우 현재 시간을 기본값으로 설정 (프론트엔드 null 에러 방지)
            // ISO 8601 형식으로 반환 (예: "2026-01-09T17:27:40.800Z")
            if (createdAtStr == null || createdAtStr.isBlank()) {
                createdAtStr = DATE_TIME_FORMATTER.format(Instant.now());
                log.warn("[Elasticsearch 로그 조회] createdAt이 null이어서 현재 시간을 기본값으로 설정: id={}, source={}", 
                    id, source.keySet());
            }

            // 간단한 필드 매핑 (camelCase 우선, snake_case 대체)
            String userId = getString(source, "userId", "user_id");
            String userRole = getString(source, "userRole", "user_role");
            String department = getString(source, "department");
            String domain = getString(source, "domain");
            String route = getString(source, "route");
            String modelName = getString(source, "modelName", "model_name");
            Boolean hasPiiInput = getBoolean(source, "hasPiiInput", "has_pii_input");
            Boolean hasPiiOutput = getBoolean(source, "hasPiiOutput", "has_pii_output");
            Boolean ragUsed = getBoolean(source, "ragUsed", "rag_used");
            Integer ragSourceCount = getInteger(source, "ragSourceCount", "rag_source_count");
            Long latencyMsTotal = getLong(source, "latencyMsTotal", "latency_ms_total");
            String errorCode = getString(source, "errorCode", "error_code");

            return new AiLogDtos.LogListItem(
                itemId,
                createdAtStr,
                userId,
                userRole,
                department,
                domain,
                route,
                modelName,
                hasPiiInput,
                hasPiiOutput,
                ragUsed,
                ragSourceCount,
                latencyMsTotal,
                errorCode
            );
        } catch (Exception e) {
            log.warn("[Elasticsearch 로그 조회] 항목 변환 중 오류: error={}", e.getMessage());
            return null;
        }
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private String getString(Map<String, Object> map, String key1, String key2) {
        Object value = map.get(key1);
        if (value == null) {
            value = map.get(key2);
        }
        return value != null ? value.toString() : null;
    }

    private Boolean getBoolean(Map<String, Object> map, String key1, String key2) {
        Object value = map.get(key1);
        if (value == null) {
            value = map.get(key2);
        }
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return null;
    }

    private Integer getInteger(Map<String, Object> map, String key1, String key2) {
        Object value = map.get(key1);
        if (value == null) {
            value = map.get(key2);
        }
        if (value == null) {
            return null;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private Long getLong(Map<String, Object> map, String key1, String key2) {
        Object value = map.get(key1);
        if (value == null) {
            value = map.get(key2);
        }
        if (value == null) {
            return null;
        }
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}

