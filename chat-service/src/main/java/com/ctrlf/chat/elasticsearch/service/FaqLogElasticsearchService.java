package com.ctrlf.chat.elasticsearch.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.ctrlf.chat.dto.response.AdminMessageLogResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Elasticsearch FAQ 로그 조회 서비스 (FAQ 초안 생성용)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FaqLogElasticsearchService {

    private final ElasticsearchClient elasticsearchClient;

    @Value("${app.elasticsearch.chat-log-index:chat_log}")
    private String chatLogIndex;

    /**
     * FAQ 초안 생성용 질문 로그 조회
     *
     * <p>Elasticsearch의 chat_log 인덱스에서 사용자 질문 로그를 조회합니다.</p>
     * <p>백엔드에서 채팅 메시지 저장 시 자동으로 저장된 로그를 조회합니다.</p>
     * <p>AI 서버에서 FAQ 자동 생성을 위해 호출하는 API에 사용됩니다.</p>
     * <p>기존 AdminMessageLogResponse 형식과 호환됩니다.</p>
     *
     * @param domain 도메인 필터 (선택)
     * @param daysBack 최근 N일간의 데이터 (기본값: 30)
     * @return FAQ 로그 목록 응답 (AdminMessageLogResponse 형식)
     */
    public AdminMessageLogResponse getFaqLogs(String domain, Integer daysBack) {
        try {
            // 기본값 설정
            int actualDaysBack = (daysBack != null && daysBack > 0) ? daysBack : 30;
            Instant startDate = Instant.now().minusSeconds(actualDaysBack * 24L * 60L * 60L);
            Instant endDate = Instant.now();

            log.info("[Elasticsearch FAQ 로그 조회] 요청: domain={}, daysBack={}, startDate={}, endDate={}, index={}",
                domain, actualDaysBack, startDate, endDate, chatLogIndex);

            // 쿼리 빌더 생성
            BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();

            // 날짜 범위 필터 (ISO 8601 형식 문자열)
            String startDateIso = startDate.toString();
            String endDateIso = endDate.toString();
            log.debug("[Elasticsearch FAQ 로그 조회] 날짜 범위 필터: createdAt >= {} AND createdAt <= {}", 
                startDateIso, endDateIso);
            boolQueryBuilder.must(Query.of(q -> q
                .range(r -> r
                    .field("createdAt")
                    .gte(JsonData.of(startDateIso))
                    .lte(JsonData.of(endDateIso))
                )
            ));

            // role = 'user' 필터 (사용자 질문만)
            log.debug("[Elasticsearch FAQ 로그 조회] role 필터: role=user");
            boolQueryBuilder.must(Query.of(q -> q
                .term(t -> t.field("role").value("user"))
            ));

            // 도메인 필터
            if (domain != null && !domain.isBlank()) {
                log.debug("[Elasticsearch FAQ 로그 조회] 도메인 필터: domain={}", domain);
                boolQueryBuilder.must(Query.of(q -> q
                    .term(t -> t.field("domain").value(domain))
                ));
            }

            BoolQuery boolQuery = boolQueryBuilder.build();

            // 검색 요청 생성 (최대 10000개 조회)
            // 인덱스: chat_log (백엔드에서 채팅 메시지 저장 시 자동 저장)
            SearchRequest searchRequest = SearchRequest.of(s -> s
                .index(chatLogIndex)  // chat_log 인덱스 (백엔드에서 채팅 메시지 저장 시 자동 저장)
                .query(Query.of(q -> q.bool(boolQuery)))
                .size(10000)  // FAQ 생성용이므로 충분히 많은 데이터 조회
                .sort(so -> so
                    .field(f -> f
                        .field("createdAt")
                        .order(SortOrder.Desc)
                    )
                )
            );

            log.info("[Elasticsearch FAQ 로그 조회] 검색 시작: index={}, queryFilters={}", 
                chatLogIndex, 
                String.format("createdAt:[%s TO %s], role:user%s", 
                    startDateIso, endDateIso, 
                    domain != null && !domain.isBlank() ? ", domain:" + domain : ""));

            // 검색 실행
            SearchResponse<Map> response = elasticsearchClient.search(searchRequest, Map.class);

            long totalHits = response.hits().total() != null ? response.hits().total().value() : 0;
            int hitsSize = response.hits().hits().size();
            
            log.info("[Elasticsearch FAQ 로그 조회] 검색 완료: totalHits={}, hits={}, index={}",
                totalHits, hitsSize, chatLogIndex);

            // 샘플 데이터 로깅 (디버깅용, 최대 3개)
            if (hitsSize > 0) {
                log.info("[Elasticsearch FAQ 로그 조회] ✅ 샘플 데이터 (최대 3개):");
                for (int i = 0; i < Math.min(3, hitsSize); i++) {
                    Hit<Map> hit = response.hits().hits().get(i);
                    Map<String, Object> source = hit.source();
                    if (source != null) {
                        // 필드명 확인 (camelCase 우선, snake_case 대체)
                        Object createdAtField = source.get("createdAt");
                        if (createdAtField == null) {
                            createdAtField = source.get("created_at");
                        }
                        Object userIdField = source.get("userId");
                        if (userIdField == null) {
                            userIdField = source.get("user_id");
                        }
                        Object sessionIdField = source.get("sessionId");
                        if (sessionIdField == null) {
                            sessionIdField = source.get("session_id");
                        }
                        log.info("[Elasticsearch FAQ 로그 조회] 샘플[{}]: id={}, content={}, role={}, domain={}, createdAt={}, userId={}, sessionId={}",
                            i, hit.id(), 
                            source.get("content"), 
                            source.get("role"),
                            source.get("domain"),
                            createdAtField,
                            userIdField,
                            sessionIdField);
                    }
                }
            } else {
                log.warn("[Elasticsearch FAQ 로그 조회] ⚠️ 조회된 데이터가 0개입니다! 인덱스={}, 필터=createdAt:[{} TO {}], role:user{}", 
                    chatLogIndex, startDateIso, endDateIso,
                    domain != null && !domain.isBlank() ? ", domain:" + domain : "");
                log.warn("[Elasticsearch FAQ 로그 조회] ⚠️ 가능한 원인:");
                log.warn("[Elasticsearch FAQ 로그 조회]   1. Elasticsearch 인덱스가 존재하지 않음: curl http://localhost:9200/_cat/indices/{}?v", chatLogIndex);
                log.warn("[Elasticsearch FAQ 로그 조회]   2. 백엔드에서 채팅 메시지 저장 시 Elasticsearch에 저장되지 않음");
                log.warn("[Elasticsearch FAQ 로그 조회]   3. 최근 채팅이 발생하지 않음");
                log.warn("[Elasticsearch FAQ 로그 조회]   4. 날짜 범위 필터가 너무 좁음 (현재: {} ~ {})", startDateIso, endDateIso);
            }

            // 결과 변환
            List<AdminMessageLogResponse.MessageLogItem> items = new ArrayList<>();
            int conversionSuccess = 0;
            int conversionFailed = 0;
            
            for (Hit<Map> hit : response.hits().hits()) {
                try {
                    Map<String, Object> source = hit.source();
                    if (source == null) {
                        conversionFailed++;
                        continue;
                    }

                    AdminMessageLogResponse.MessageLogItem item = convertToMessageLogItem(hit.id(), source);
                    if (item != null) {
                        items.add(item);
                        conversionSuccess++;
                    } else {
                        conversionFailed++;
                    }
                } catch (Exception e) {
                    conversionFailed++;
                    log.warn("[Elasticsearch FAQ 로그 조회] 항목 변환 실패: id={}, error={}", hit.id(), e.getMessage());
                }
            }

            long totalCount = totalHits;

            log.info("[Elasticsearch FAQ 로그 조회] 변환 완료: totalHits={}, items={}, conversionSuccess={}, conversionFailed={}", 
                totalCount, items.size(), conversionSuccess, conversionFailed);

            return new AdminMessageLogResponse(items, (int) totalCount);

        } catch (ElasticsearchException e) {
            // Elasticsearch 예외 처리 (인덱스가 없거나 검색 오류)
            if (e.getMessage() != null && e.getMessage().contains("index_not_found_exception")) {
                log.warn("[Elasticsearch FAQ 로그 조회] 인덱스가 없습니다: index={}, message={}. 빈 결과를 반환합니다.",
                    chatLogIndex, e.getMessage());
                // 인덱스가 없으면 빈 결과 반환 (백엔드가 아직 채팅 로그를 저장하지 않았을 수 있음)
                return new AdminMessageLogResponse(new ArrayList<>(), 0);
            } else {
                log.error("[Elasticsearch FAQ 로그 조회] Elasticsearch 오류 발생: index={}, error={}",
                    chatLogIndex, e.getMessage(), e);
                throw new RuntimeException("FAQ 로그 조회 실패: " + e.getMessage(), e);
            }
        } catch (Exception e) {
            log.error("[Elasticsearch FAQ 로그 조회] 오류 발생: index={}, error={}",
                chatLogIndex, e.getMessage(), e);
            throw new RuntimeException("FAQ 로그 조회 실패: " + e.getMessage(), e);
        }
    }

    /**
     * Map을 MessageLogItem으로 변환
     */
    @SuppressWarnings("unchecked")
    private AdminMessageLogResponse.MessageLogItem convertToMessageLogItem(String id, Map<String, Object> source) {
        try {
            UUID itemId = null;
            UUID sessionId = null;
            String content = null;
            String keyword = null;
            String domain = null;
            UUID userId = null;
            Instant createdAt = null;

            // ID 변환
            if (id != null) {
                try {
                    itemId = UUID.fromString(id);
                } catch (IllegalArgumentException e) {
                    // Elasticsearch ID가 UUID 형식이 아닐 수 있음
                    log.debug("[Elasticsearch FAQ 로그 조회] ID를 UUID로 변환 실패: id={}", id);
                }
            }

            // sessionId 변환 (camelCase 우선, snake_case 대체)
            Object sessionIdObj = source.get("sessionId");
            if (sessionIdObj == null) {
                sessionIdObj = source.get("session_id");  // snake_case 대체
            }
            if (sessionIdObj != null) {
                if (sessionIdObj instanceof String) {
                    try {
                        sessionId = UUID.fromString((String) sessionIdObj);
                    } catch (IllegalArgumentException e) {
                        log.debug("[Elasticsearch FAQ 로그 조회] sessionId를 UUID로 변환 실패: sessionId={}", sessionIdObj);
                    }
                }
            }

            // createdAt 변환 (camelCase 우선, snake_case 대체)
            Object createdAtObj = source.get("createdAt");
            if (createdAtObj == null) {
                createdAtObj = source.get("created_at");  // snake_case 대체
            }
            if (createdAtObj != null) {
                if (createdAtObj instanceof String) {
                    createdAt = Instant.parse((String) createdAtObj);
                } else if (createdAtObj instanceof Number) {
                    createdAt = Instant.ofEpochMilli(((Number) createdAtObj).longValue());
                }
            }

            // 간단한 필드 매핑
            content = getString(source, "content");
            keyword = getString(source, "keyword");
            domain = getString(source, "domain");
            if (domain == null || domain.isBlank()) {
                domain = "ETC";  // 기본값
            }

            // userId 변환 (camelCase 우선, snake_case 대체)
            Object userIdObj = source.get("userId");
            if (userIdObj == null) {
                userIdObj = source.get("user_id");  // snake_case 대체
            }
            if (userIdObj != null) {
                if (userIdObj instanceof String) {
                    try {
                        userId = UUID.fromString((String) userIdObj);
                    } catch (IllegalArgumentException e) {
                        log.debug("[Elasticsearch FAQ 로그 조회] userId를 UUID로 변환 실패: userId={}", userIdObj);
                    }
                }
            }

            return new AdminMessageLogResponse.MessageLogItem(
                itemId,
                sessionId,
                content,
                keyword,
                domain,
                userId,
                createdAt
            );
        } catch (Exception e) {
            log.warn("[Elasticsearch FAQ 로그 조회] 항목 변환 중 오류: error={}", e.getMessage());
            return null;
        }
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }
}

