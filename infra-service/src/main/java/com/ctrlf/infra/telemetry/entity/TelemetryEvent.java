package com.ctrlf.infra.telemetry.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 텔레메트리 이벤트 엔티티
 * 
 * <p>AI에서 수집한 구조화 이벤트를 저장합니다.</p>
 */
@Entity
@Table(name = "telemetry_event", schema = "telemetry")
@Getter
@Setter
@NoArgsConstructor
public class TelemetryEvent {

    /** Idempotency key (AI eventId) */
    @Id
    @Column(name = "event_id", columnDefinition = "uuid")
    private UUID eventId;

    /** ai-gateway 등 */
    @Column(name = "source", length = 50, nullable = false)
    private String source;

    /** AI가 전송한 시각 */
    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    /** CHAT_TURN | FEEDBACK | SECURITY */
    @Column(name = "event_type", length = 30, nullable = false)
    private String eventType;

    /** X-Trace-Id (UUID 또는 문자열) */
    @Column(name = "trace_id", length = 200, nullable = false)
    private String traceId;

    /** X-Conversation-Id */
    @Column(name = "conversation_id", length = 100)
    private String conversationId;

    /** X-Turn-Id */
    @Column(name = "turn_id")
    private Integer turnId;

    /** X-User-Id (또는 user_uuid) */
    @Column(name = "user_id", length = 64, nullable = false)
    private String userId;

    /** X-Dept-Id */
    @Column(name = "dept_id", length = 64, nullable = false)
    private String deptId;

    /** occurredAt */
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    /** eventType별 payload 원문 */
    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private Object payload;

    /** 백엔드 수신 시각 */
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;
}

