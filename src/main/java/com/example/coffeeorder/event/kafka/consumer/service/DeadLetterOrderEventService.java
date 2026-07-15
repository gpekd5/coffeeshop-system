package com.example.coffeeorder.event.kafka.consumer.service;

import java.time.Clock;
import java.time.LocalDateTime;

import com.example.coffeeorder.event.kafka.consumer.entity.DeadLetterOrderEvent;
import com.example.coffeeorder.event.kafka.consumer.repository.DeadLetterOrderEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeadLetterOrderEventService {

    private final DeadLetterOrderEventRepository deadLetterOrderEventRepository;
    private final Clock clock;

    public DeadLetterOrderEventService(
            DeadLetterOrderEventRepository deadLetterOrderEventRepository,
            Clock clock
    ) {
        this.deadLetterOrderEventRepository = deadLetterOrderEventRepository;
        this.clock = clock;
    }

    @Transactional
    public DeadLetterOrderEvent record(
            String eventId,
            String originalTopic,
            String deadLetterTopic,
            Integer kafkaPartition,
            Long kafkaOffset,
            String payload,
            String failureReason
    ) {
        return deadLetterOrderEventRepository.saveAndFlush(
                DeadLetterOrderEvent.of(
                        eventId,
                        originalTopic,
                        deadLetterTopic,
                        kafkaPartition,
                        kafkaOffset,
                        payload,
                        failureReason,
                        LocalDateTime.now(clock)
                )
        );
    }
}
