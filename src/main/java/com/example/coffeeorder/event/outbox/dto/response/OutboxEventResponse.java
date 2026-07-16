package com.example.coffeeorder.event.outbox.dto.response;

import java.time.LocalDateTime;

import com.example.coffeeorder.event.outbox.entity.OutboxEvent;
import com.example.coffeeorder.event.outbox.entity.OutboxStatus;

public record OutboxEventResponse(
        String eventId,
        String aggregateType,
        Long aggregateId,
        String eventType,
        OutboxStatus status,
        int retryCount,
        LocalDateTime nextRetryAt,
        String lastError,
        LocalDateTime publishedAt,
        LocalDateTime createdAt
) {

    public static OutboxEventResponse from(OutboxEvent event) {
        return new OutboxEventResponse(
                event.getId(),
                event.getAggregateType(),
                event.getAggregateId(),
                event.getEventType(),
                event.getStatus(),
                event.getRetryCount(),
                event.getNextRetryAt(),
                event.getLastError(),
                event.getPublishedAt(),
                event.getCreatedAt()
        );
    }
}
