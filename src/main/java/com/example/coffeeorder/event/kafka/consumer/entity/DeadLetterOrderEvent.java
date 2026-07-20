package com.example.coffeeorder.event.kafka.consumer.entity;

import java.time.LocalDateTime;

import com.example.coffeeorder.common.entity.BaseEntity;
import com.example.coffeeorder.common.exception.BusinessException;
import com.example.coffeeorder.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "dead_letter_order_events")
public class DeadLetterOrderEvent extends BaseEntity {

    private static final int MAX_FAILURE_REASON_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "event_id",
            length = 36
    )
    private String eventId;

    @Column(
            name = "original_topic",
            nullable = false,
            length = 100
    )
    private String originalTopic;

    @Column(
            name = "dead_letter_topic",
            nullable = false,
            length = 100
    )
    private String deadLetterTopic;

    @Column(
            name = "kafka_partition",
            nullable = false
    )
    private Integer kafkaPartition;

    @Column(
            name = "kafka_offset",
            nullable = false
    )
    private Long kafkaOffset;

    @Lob
    @Column(
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String payload;

    @Column(
            name = "failure_reason",
            length = MAX_FAILURE_REASON_LENGTH
    )
    private String failureReason;

    @Column(
            name = "received_at",
            nullable = false
    )
    private LocalDateTime receivedAt;

    protected DeadLetterOrderEvent() {
    }

    private DeadLetterOrderEvent(
            String eventId,
            String originalTopic,
            String deadLetterTopic,
            Integer kafkaPartition,
            Long kafkaOffset,
            String payload,
            String failureReason,
            LocalDateTime receivedAt
    ) {
        validateRequired(
                originalTopic,
                deadLetterTopic,
                kafkaPartition,
                kafkaOffset,
                payload,
                receivedAt
        );

        this.eventId = eventId;
        this.originalTopic = originalTopic;
        this.deadLetterTopic = deadLetterTopic;
        this.kafkaPartition = kafkaPartition;
        this.kafkaOffset = kafkaOffset;
        this.payload = payload;
        this.failureReason = truncate(failureReason);
        this.receivedAt = receivedAt;
    }

    public static DeadLetterOrderEvent of(
            String eventId,
            String originalTopic,
            String deadLetterTopic,
            Integer kafkaPartition,
            Long kafkaOffset,
            String payload,
            String failureReason,
            LocalDateTime receivedAt
    ) {
        return new DeadLetterOrderEvent(
                eventId,
                originalTopic,
                deadLetterTopic,
                kafkaPartition,
                kafkaOffset,
                payload,
                failureReason,
                receivedAt
        );
    }

    private static void validateRequired(
            String originalTopic,
            String deadLetterTopic,
            Integer kafkaPartition,
            Long kafkaOffset,
            String payload,
            LocalDateTime receivedAt
    ) {
        if (originalTopic == null || originalTopic.isBlank()
                || deadLetterTopic == null || deadLetterTopic.isBlank()
                || kafkaPartition == null
                || kafkaOffset == null
                || payload == null || payload.isBlank()
                || receivedAt == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private String truncate(String value) {
        if (value == null || value.length() <= MAX_FAILURE_REASON_LENGTH) {
            return value;
        }

        return value.substring(
                0,
                MAX_FAILURE_REASON_LENGTH
        );
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getOriginalTopic() {
        return originalTopic;
    }

    public String getDeadLetterTopic() {
        return deadLetterTopic;
    }

    public Integer getKafkaPartition() {
        return kafkaPartition;
    }

    public Long getKafkaOffset() {
        return kafkaOffset;
    }

    public String getPayload() {
        return payload;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }
}
