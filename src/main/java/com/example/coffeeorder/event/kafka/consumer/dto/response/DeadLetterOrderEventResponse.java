package com.example.coffeeorder.event.kafka.consumer.dto.response;

import java.time.LocalDateTime;

import com.example.coffeeorder.event.kafka.consumer.entity.DeadLetterOrderEvent;

public record DeadLetterOrderEventResponse(
        Long id,
        String eventId,
        String topic,
        String originalTopic,
        Integer kafkaPartition,
        Long kafkaOffset,
        String exceptionMessage,
        String payload,
        LocalDateTime receivedAt,
        LocalDateTime createdAt
) {

    public static DeadLetterOrderEventResponse from(DeadLetterOrderEvent event) {
        return new DeadLetterOrderEventResponse(
                event.getId(),
                event.getEventId(),
                event.getDeadLetterTopic(),
                event.getOriginalTopic(),
                event.getKafkaPartition(),
                event.getKafkaOffset(),
                event.getFailureReason(),
                event.getPayload(),
                event.getReceivedAt(),
                event.getCreatedAt()
        );
    }
}
