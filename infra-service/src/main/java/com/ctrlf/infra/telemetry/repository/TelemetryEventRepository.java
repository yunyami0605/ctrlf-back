package com.ctrlf.infra.telemetry.repository;

import com.ctrlf.infra.telemetry.entity.TelemetryEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * 텔레메트리 이벤트 Repository
 */
@Repository
public interface TelemetryEventRepository extends JpaRepository<TelemetryEvent, UUID> {

    /**
     * eventId로 조회 (Idempotency 체크용)
     */
    Optional<TelemetryEvent> findByEventId(UUID eventId);

    /**
     * 기간 및 부서별 이벤트 조회
     */
    @Query("SELECT e FROM TelemetryEvent e WHERE e.occurredAt >= :startDate AND e.occurredAt < :endDate " +
           "AND (:deptId IS NULL OR e.deptId = :deptId OR :deptId = 'all')")
    List<TelemetryEvent> findByPeriodAndDept(
        @Param("startDate") Instant startDate,
        @Param("endDate") Instant endDate,
        @Param("deptId") String deptId
    );

    /**
     * 이벤트 타입별 기간 조회
     */
    @Query("SELECT e FROM TelemetryEvent e WHERE e.eventType = :eventType " +
           "AND e.occurredAt >= :startDate AND e.occurredAt < :endDate " +
           "AND (:deptId IS NULL OR e.deptId = :deptId OR :deptId = 'all')")
    List<TelemetryEvent> findByEventTypeAndPeriodAndDept(
        @Param("eventType") String eventType,
        @Param("startDate") Instant startDate,
        @Param("endDate") Instant endDate,
        @Param("deptId") String deptId
    );
}

