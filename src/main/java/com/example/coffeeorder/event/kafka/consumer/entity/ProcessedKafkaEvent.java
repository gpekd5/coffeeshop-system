package com.example.coffeeorder.event.kafka.consumer.entity;

import java.time.LocalDateTime;

import com.example.coffeeorder.common.entity.BaseEntity;
import com.example.coffeeorder.common.exception.BusinessException;
import com.example.coffeeorder.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "processed_kafka_events")
public class ProcessedKafkaEvent extends BaseEntity {

    private static final int MAX_LAST_ERROR_LENGTH = 1000;

    @Id
    @Column(
            name = "event_id",
            length = 36,
            nullable = false,
            updatable = false
    )
    private String eventId;

    @Column(
            name = "event_type",
            nullable = false,
            length = 50
    )
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 20
    )
    private KafkaEventProcessingStatus status;

    @Column(
            nullable = false,
            length = 100
    )
    private String topic;

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

    @Column(
            name = "attempt_count",
            nullable = false
    )
    private int attemptCount;

    @Column(
            name = "last_error",
            length = MAX_LAST_ERROR_LENGTH
    )
    private String lastError;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    protected ProcessedKafkaEvent() {
    }

    private ProcessedKafkaEvent(
            String eventId,
            String eventType,
            String topic,
            Integer kafkaPartition,
            Long kafkaOffset
    ) {
        validateRequired(
                eventId,
                eventType,
                topic,
                kafkaPartition,
                kafkaOffset
        );

        this.eventId = eventId;
        this.eventType = eventType;
        this.topic = topic;
        this.kafkaPartition = kafkaPartition;
        this.kafkaOffset = kafkaOffset;
        this.status = KafkaEventProcessingStatus.PROCESSING;
        this.attemptCount = 1;
    }

    public static ProcessedKafkaEvent start(
            String eventId,
            String eventType,
            String topic,
            Integer kafkaPartition,
            Long kafkaOffset
    ) {
        return new ProcessedKafkaEvent(
                eventId,
                eventType,
                topic,
                kafkaPartition,
                kafkaOffset
        );
    }

    public void startRetry(
            String topic,
            Integer kafkaPartition,
            Long kafkaOffset
    ) {
        validateRequired(
                eventId,
                eventType,
                topic,
                kafkaPartition,
                kafkaOffset
        );

        this.topic = topic;
        this.kafkaPartition = kafkaPartition;
        this.kafkaOffset = kafkaOffset;
        this.status = KafkaEventProcessingStatus.PROCESSING;
        this.attemptCount += 1;
    }

    public void complete(LocalDateTime processedAt) {
        if (processedAt == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        this.status = KafkaEventProcessingStatus.COMPLETED;
        this.lastError = null;
        this.processedAt = processedAt;
    }

    public void fail(String lastError) {
        this.status = KafkaEventProcessingStatus.FAILED;
        this.lastError = truncate(lastError);
    }

    public boolean isCompleted() {
        return status == KafkaEventProcessingStatus.COMPLETED;
    }

    public boolean isProcessing() {
        return status == KafkaEventProcessingStatus.PROCESSING;
    }

    private static void validateRequired(
            String eventId,
            String eventType,
            String topic,
            Integer kafkaPartition,
            Long kafkaOffset
    ) {
        if (eventId == null || eventId.isBlank()
                || eventType == null || eventType.isBlank()
                || topic == null || topic.isBlank()
                || kafkaPartition == null
                || kafkaOffset == null) {
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

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public KafkaEventProcessingStatus getStatus() {
        return status;
    }

    public String getTopic() {
        return topic;
    }

    public Integer getKafkaPartition() {
        return kafkaPartition;
    }

    public Long getKafkaOffset() {
        return kafkaOffset;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public String getLastError() {
        return lastError;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }
}
