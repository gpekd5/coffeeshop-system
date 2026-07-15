package com.example.coffeeorder.event.kafka.consumer.service;

import java.time.Clock;
import java.time.LocalDateTime;

import com.example.coffeeorder.common.exception.BusinessException;
import com.example.coffeeorder.common.exception.ErrorCode;
import com.example.coffeeorder.event.kafka.consumer.entity.ProcessedKafkaEvent;
import com.example.coffeeorder.event.kafka.consumer.repository.ProcessedKafkaEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProcessedKafkaEventService {

    private final ProcessedKafkaEventRepository processedKafkaEventRepository;
    private final Clock clock;

    public ProcessedKafkaEventService(
            ProcessedKafkaEventRepository processedKafkaEventRepository,
            Clock clock
    ) {
        this.processedKafkaEventRepository = processedKafkaEventRepository;
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
                            kafkaOffset
                    )
            );

            return true;
        }

        if (event.isCompleted() || event.isProcessing()) {
            return false;
        }

        event.startRetry(
                topic,
                kafkaPartition,
                kafkaOffset
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
}
