package com.example.coffeeorder.event.outbox.entity;

import java.time.LocalDateTime;

import com.example.coffeeorder.common.entity.BaseEntity;
import com.example.coffeeorder.common.exception.BusinessException;
import com.example.coffeeorder.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "outbox_events",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_outbox_events_aggregate_event",
                columnNames = {
                        "aggregate_type",
                        "aggregate_id",
                        "event_type"
                }
        )
)
public class OutboxEvent extends BaseEntity {

    private static final String ORDER_AGGREGATE_TYPE = "ORDER";
    private static final String ORDER_COMPLETED_EVENT_TYPE = "ORDER_COMPLETED";
    private static final int MAX_LAST_ERROR_LENGTH = 1000;

    @Id
    @Column(
            length = 36,
            nullable = false,
            updatable = false
    )
    private String id;

    @Column(
            name = "aggregate_type",
            nullable = false,
            length = 50
    )
    private String aggregateType;

    @Column(
            name = "aggregate_id",
            nullable = false
    )
    private Long aggregateId;

    @Column(
            name = "event_type",
            nullable = false,
            length = 50
    )
    private String eventType;

    @Lob
    @Column(nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 20
    )
    private OutboxStatus status;

    @Column(
            name = "retry_count",
            nullable = false
    )
    private int retryCount;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(
            name = "last_error",
            length = MAX_LAST_ERROR_LENGTH
    )
    private String lastError;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    protected OutboxEvent() {
    }

    private OutboxEvent(
            String id,
            String aggregateType,
            Long aggregateId,
            String eventType,
            String payload
    ) {
        validateRequired(
                id,
                aggregateType,
                aggregateId,
                eventType,
                payload
        );

        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.retryCount = 0;
    }

    public static OutboxEvent orderCompleted(
            String eventId,
            Long orderId,
            String payload
    ) {
        return new OutboxEvent(
                eventId,
                ORDER_AGGREGATE_TYPE,
                orderId,
                ORDER_COMPLETED_EVENT_TYPE,
                payload
        );
    }

    public void markPublished(LocalDateTime publishedAt) {
        if (publishedAt == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = publishedAt;
        this.nextRetryAt = null;
        this.lastError = null;
    }

    public void markPublishFailed(
            String lastError,
            LocalDateTime nextRetryAt
    ) {
        this.status = OutboxStatus.FAILED;
        this.retryCount += 1;
        this.lastError = truncate(lastError);
        this.nextRetryAt = nextRetryAt;
    }

    public void reserveForPublish(LocalDateTime reservedUntil) {
        if (reservedUntil == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        this.status = OutboxStatus.PENDING;
        this.nextRetryAt = reservedUntil;
    }

    public void resetForRetry(LocalDateTime nextRetryAt) {
        if (status != OutboxStatus.FAILED) {
            throw new BusinessException(ErrorCode.OUTBOX_EVENT_RETRY_NOT_ALLOWED);
        }

        this.status = OutboxStatus.PENDING;
        this.nextRetryAt = nextRetryAt;
    }

    public boolean isPublishable(LocalDateTime now) {
        if (status == OutboxStatus.PUBLISHED) {
            return false;
        }

        if (nextRetryAt == null) {
            return status == OutboxStatus.PENDING;
        }

        return !nextRetryAt.isAfter(now);
    }

    private static void validateRequired(
            String id,
            String aggregateType,
            Long aggregateId,
            String eventType,
            String payload
    ) {
        if (id == null || id.isBlank()
                || aggregateType == null || aggregateType.isBlank()
                || aggregateId == null
                || eventType == null || eventType.isBlank()
                || payload == null || payload.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private String truncate(String value) {
        if (value == null || value.length() <= MAX_LAST_ERROR_LENGTH) {
            return value;
        }

        return value.substring(
                0,
                MAX_LAST_ERROR_LENGTH
        );
    }

    public String getId() {
        return id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public Long getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public LocalDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    public String getLastError() {
        return lastError;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }
}
