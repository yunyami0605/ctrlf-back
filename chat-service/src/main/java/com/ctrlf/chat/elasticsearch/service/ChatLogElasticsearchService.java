package com.ctrlf.chat.elasticsearch.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.ctrlf.chat.dto.response.ChatLogDtos;
import com.ctrlf.chat.entity.ChatMessage;
import com.ctrlf.chat.entity.ChatSession;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Elasticsearch 채팅 로그 조회 및 저장 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatLogElasticsearchService {

    private final ElasticsearchClient elasticsearchClient;

    @Value("${app.elasticsearch.chat-log-index:chat_log}")
    private String chatLogIndex;

    /**
     * 채팅 로그를 Elasticsearch에 저장
     *
     * <p>Elasticsearch의 chat_log 인덱스에 저장합니다.</p>
     *
     * @param message 채팅 메시지
     * @param session 채팅 세션
     * @param userId 사용자 ID
     * @param domain 도메인
     * @param department 부서
     */
    public void saveChatLog(ChatMessage message, ChatSession session, String userId, String domain, String department) {
        try {
            
            Map<String, Object> logData = new HashMap<>();
            
            // 기본 필드
            logData.put("id", message.getId().toString());
            logData.put("sessionId", message.getSessionId().toString());
            logData.put("createdAt", message.getCreatedAt().toString());
            logData.put("userId", userId);
            logData.put("domain", domain != null ? domain : "ETC");
            logData.put("department", department);
            logData.put("role", message.getRole());
            logData.put("content", message.getContent());
            logData.put("keyword", message.getKeyword());
            
            // AI 응답 관련 필드 (assistant 메시지인 경우)
            if ("assistant".equals(message.getRole())) {
                logData.put("route", message.getRoutingType());
                logData.put("modelName", message.getLlmModel());
                logData.put("answer", message.getContent());
                logData.put("hasPiiInput", false);  // AI 서버에서 전송할 때 설정
                logData.put("hasPiiOutput", message.getPiiDetected() != null ? message.getPiiDetected() : false);
                logData.put("ragUsed", message.getRoutingType() != null && message.getRoutingType().contains("RAG"));
                logData.put("ragSourceCount", null);  // AI 서버에서 전송할 때 설정
                logData.put("latencyMsTotal", message.getResponseTimeMs());
                logData.put("errorCode", message.getIsError() != null && message.getIsError() ? "ERROR" : null);
            } else {
                // user 메시지인 경우
                logData.put("question", message.getContent());
                logData.put("hasPiiInput", message.getPiiDetected() != null ? message.getPiiDetected() : false);
            }
            
            // 세션 정보
            if (session != null) {
                logData.put("conversationId", session.getId().toString());
            }
            
            // Elasticsearch에 저장 (동기적으로 실행하여 저장 완료 보장)
            IndexRequest<Map<String, Object>> request = IndexRequest.of(i -> i
                .index(chatLogIndex)
                .id(message.getId().toString())
                .document(logData)
            );
            
            // 동기적으로 실행하여 저장 완료 보장
            var response = elasticsearchClient.index(request);
            
            log.info("[Elasticsearch 채팅 로그 저장] 성공: messageId={}, role={}, content={}, index={}, result={}, version={}",
                message.getId(), message.getRole(), 
                message.getContent() != null && message.getContent().length() > 50 
                    ? message.getContent().substring(0, 50) + "..." 
                    : message.getContent(),
                chatLogIndex, response.result(), response.version());
                
        } catch (Exception e) {
            log.error("[Elasticsearch 채팅 로그 저장] 실패: messageId={}, role={}, content={}, error={}",
                message.getId(), message.getRole(),
                message.getContent() != null && message.getContent().length() > 50 
                    ? message.getContent().substring(0, 50) + "..." 
                    : message.getContent(),
                e.getMessage(), e);
            // Elasticsearch 저장 실패해도 채팅 기능은 계속 동작하도록 예외를 던지지 않음
        }
    }


    /**
     * 채팅 로그 조회
     *
     * @param period 기간 (7 | 30 | 90)
     * @param startDate 시작 날짜 (ISO 8601)
     * @param endDate 종료 날짜 (ISO 8601)
     * @param department 부서명 필터
     * @param domain 도메인 필터
     * @param route 라우트 필터
     * @param model 모델명 필터
     * @param onlyError 에러만 보기
     * @param hasPiiOnly PII 포함만 보기
     * @param page 페이지 번호
     * @param size 페이지 크기
     * @param sort 정렬 (예: createdAt,desc)
     * @return 채팅 로그 페이지 응답
     */
    public ChatLogDtos.PageResponse<ChatLogDtos.ChatLogItem> getChatLogs(
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
            log.info("[Elasticsearch 채팅 로그 조회] 요청: period={}, startDate={}, endDate={}, department={}, domain={}, route={}, model={}, onlyError={}, hasPiiOnly={}, page={}, size={}, sort={}",
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

            // 모델 필터
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

            log.info("[Elasticsearch 채팅 로그 조회] 검색 시작: index={}, from={}, size={}", chatLogIndex, from, pageSize);

            // 검색 실행
            SearchResponse<Map> response = elasticsearchClient.search(searchRequest, Map.class);

            log.info("[Elasticsearch 채팅 로그 조회] 검색 완료: totalHits={}, hits={}",
                response.hits().total() != null ? response.hits().total().value() : 0,
                response.hits().hits().size());

            // 결과 변환
            List<ChatLogDtos.ChatLogItem> items = new ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                try {
                    Map<String, Object> source = hit.source();
                    if (source == null) {
                        continue;
                    }

                    ChatLogDtos.ChatLogItem item = convertToChatLogItem(hit.id(), source);
                    items.add(item);
                } catch (Exception e) {
                    log.warn("[Elasticsearch 채팅 로그 조회] 항목 변환 실패: id={}, error={}", hit.id(), e.getMessage());
                }
            }

            // 총 개수 및 페이지 수 계산
            long totalHits = response.hits().total() != null ? response.hits().total().value() : 0;
            int totalPages = (int) Math.ceil((double) totalHits / pageSize);

            log.info("[Elasticsearch 채팅 로그 조회] 변환 완료: items={}, totalHits={}, totalPages={}", items.size(), totalHits, totalPages);

            return new ChatLogDtos.PageResponse<>(
                items,
                totalHits,
                totalPages,
                pageNumber,
                pageSize
            );

        } catch (Exception e) {
            log.error("[Elasticsearch 채팅 로그 조회] 오류 발생", e);
            throw new RuntimeException("채팅 로그 조회 실패: " + e.getMessage(), e);
        }
    }

    /**
     * Map을 ChatLogItem으로 변환
     */
    @SuppressWarnings("unchecked")
    private ChatLogDtos.ChatLogItem convertToChatLogItem(String id, Map<String, Object> source) {
        ChatLogDtos.ChatLogItem item = new ChatLogDtos.ChatLogItem();
        item.setId(id);

        // createdAt 변환
        Object createdAtObj = source.get("createdAt");
        if (createdAtObj != null) {
            if (createdAtObj instanceof String) {
                item.setCreatedAt(Instant.parse((String) createdAtObj));
            } else if (createdAtObj instanceof Number) {
                item.setCreatedAt(Instant.ofEpochMilli(((Number) createdAtObj).longValue()));
            }
        }

        // 간단한 필드 매핑
        item.setUserId(getString(source, "userId"));
        item.setUserRole(getString(source, "role"));
        item.setDepartment(getString(source, "department"));
        item.setDomain(getString(source, "domain"));
        item.setRoute(getString(source, "route"));
        item.setModelName(getString(source, "modelName"));
        item.setQuestion(getString(source, "question"));
        item.setAnswer(getString(source, "answer"));
        item.setTraceId(getString(source, "traceId"));
        item.setConversationId(getString(source, "conversationId"));
        item.setErrorCode(getString(source, "errorCode"));

        // Boolean 필드
        item.setHasPiiInput(getBoolean(source, "hasPiiInput"));
        item.setHasPiiOutput(getBoolean(source, "hasPiiOutput"));
        item.setRagUsed(getBoolean(source, "ragUsed"));

        // Integer 필드
        item.setRagSourceCount(getInteger(source, "ragSourceCount"));
        item.setTurnId(getInteger(source, "turnId"));

        // Long 필드
        item.setLatencyMsTotal(getLong(source, "latencyMsTotal"));

        return item;
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private Boolean getBoolean(Map<String, Object> map, String key) {
        Object value = map.get(key);
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

    private Integer getInteger(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
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

    private Long getLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
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

    /**
     * 기간 계산
     */
    private Instant[] calculatePeriodRange(String period, String startDateStr, String endDateStr) {
        // startDate/endDate가 있으면 우선 사용
        if (startDateStr != null && !startDateStr.isBlank()) {
            try {
                Instant startDate = Instant.parse(startDateStr);
                Instant endDate = endDateStr != null && !endDateStr.isBlank()
                    ? Instant.parse(endDateStr)
                    : Instant.now();
                return new Instant[]{startDate, endDate};
            } catch (Exception e) {
                log.warn("[Elasticsearch 채팅 로그 조회] 날짜 파싱 실패: startDate={}, endDate={}, error={}",
                    startDateStr, endDateStr, e.getMessage());
            }
        }

        // period 사용
        if (period == null || period.isBlank()) {
            period = "30";
        }

        Instant endDate = Instant.now();
        Instant startDate;
        switch (period) {
            case "7":
                startDate = endDate.minusSeconds(7 * 24L * 60L * 60L);
                break;
            case "30":
                startDate = endDate.minusSeconds(30L * 24L * 60L * 60L);
                break;
            case "90":
                startDate = endDate.minusSeconds(90L * 24L * 60L * 60L);
                break;
            default:
                startDate = endDate.minusSeconds(30L * 24L * 60L * 60L);
        }

        return new Instant[]{startDate, endDate};
    }
}
