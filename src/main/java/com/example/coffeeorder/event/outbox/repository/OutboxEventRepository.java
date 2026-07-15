package com.example.coffeeorder.event.outbox.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.example.coffeeorder.event.outbox.entity.OutboxEvent;
import com.example.coffeeorder.event.outbox.entity.OutboxStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository
        extends JpaRepository<OutboxEvent, String> {

    Optional<OutboxEvent> findByAggregateTypeAndAggregateIdAndEventType(
            String aggregateType,
            Long aggregateId,
            String eventType
    );

    List<OutboxEvent> findAllByStatusOrderByCreatedAtAsc(OutboxStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select event
            from OutboxEvent event
            where (
                event.status = :pendingStatus
                and (
                    event.nextRetryAt is null
                    or event.nextRetryAt <= :now
                )
            ) or (
                event.status = :failedStatus
                and event.nextRetryAt is not null
                and event.nextRetryAt <= :now
            )
            order by event.createdAt asc, event.id asc
            """)
    List<OutboxEvent> findPublishableEventsForUpdate(
            @Param("now") LocalDateTime now,
            @Param("pendingStatus") OutboxStatus pendingStatus,
            @Param("failedStatus") OutboxStatus failedStatus,
            Pageable pageable
    );
}
