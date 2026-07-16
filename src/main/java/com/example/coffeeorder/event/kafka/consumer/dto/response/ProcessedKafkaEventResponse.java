package com.example.coffeeorder.event.kafka.consumer.dto.response;

import java.time.LocalDateTime;

import com.example.coffeeorder.event.kafka.consumer.entity.KafkaEventProcessingStatus;
import com.example.coffeeorder.event.kafka.consumer.entity.ProcessedKafkaEvent;

public record ProcessedKafkaEventResponse(
        String eventId,
        String eventType,
        KafkaEventProcessingStatus status,
        String topic,
        Integer kafkaPartition,
        Long kafkaOffset,
        int attemptCount,
        String lastError,
        LocalDateTime processingDeadlineAt,
        LocalDateTime processedAt,
        LocalDateTime createdAt
) {

    public static ProcessedKafkaEventResponse from(ProcessedKafkaEvent event) {
        return new ProcessedKafkaEventResponse(
                event.getEventId(),
                event.getEventType(),
                event.getStatus(),
                event.getTopic(),
                event.getKafkaPartition(),
                event.getKafkaOffset(),
                event.getAttemptCount(),
                event.getLastError(),
                event.getProcessingDeadlineAt(),
                event.getProcessedAt(),
                event.getCreatedAt()
        );
    }
}
