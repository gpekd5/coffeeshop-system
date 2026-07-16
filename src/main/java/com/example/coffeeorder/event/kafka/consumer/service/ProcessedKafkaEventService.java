package com.example.coffeeorder.event.kafka.consumer.service;

import java.time.Clock;
import java.time.LocalDateTime;

import com.example.coffeeorder.common.exception.BusinessException;
import com.example.coffeeorder.common.exception.ErrorCode;
import com.example.coffeeorder.event.kafka.config.KafkaOrderEventProperties;
import com.example.coffeeorder.event.kafka.consumer.entity.ProcessedKafkaEvent;
import com.example.coffeeorder.event.kafka.consumer.repository.ProcessedKafkaEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProcessedKafkaEventService {

    private final ProcessedKafkaEventRepository processedKafkaEventRepository;
    private final KafkaOrderEventProperties properties;
    private final Clock clock;

    public ProcessedKafkaEventService(
            ProcessedKafkaEventRepository processedKafkaEventRepository,
            KafkaOrderEventProperties properties,
            Clock clock
    ) {
        this.processedKafkaEventRepository = processedKafkaEventRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public boolean beginProcessing(
            String eventId,
            String eventType,
            String topic,
            Integer kafkaPartition,
            Long kafkaOffset
    ) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime processingDeadlineAt = processingDeadlineAt(now);
        ProcessedKafkaEvent event = processedKafkaEventRepository
                .findByEventIdForUpdate(eventId)
                .orElse(null);

        if (event == null) {
            processedKafkaEventRepository.saveAndFlush(
                    ProcessedKafkaEvent.start(
                            eventId,
                            eventType,
                            topic,
                            kafkaPartition,
                            kafkaOffset,
                            processingDeadlineAt
                    )
            );

            return true;
        }

        if (event.isCompleted() || event.isProcessingLeaseActive(now)) {
            return false;
        }

        event.startRetry(
                topic,
                kafkaPartition,
                kafkaOffset,
                processingDeadlineAt
        );

        return true;
    }

    @Transactional
    public void complete(String eventId) {
        ProcessedKafkaEvent event = findEvent(eventId);

        event.complete(LocalDateTime.now(clock));
    }

    @Transactional
    public void fail(
            String eventId,
            String lastError
    ) {
        ProcessedKafkaEvent event = findEvent(eventId);

        event.fail(lastError);
    }

    private ProcessedKafkaEvent findEvent(String eventId) {
        return processedKafkaEventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INTERNAL_SERVER_ERROR
                ));
    }

    private LocalDateTime processingDeadlineAt(LocalDateTime now) {
        long processingLeaseSeconds = properties.consumer()
                .processingLeaseSeconds();

        if (processingLeaseSeconds < 1) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        return now.plusSeconds(processingLeaseSeconds);
    }
}
