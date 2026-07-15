package com.example.coffeeorder.event.kafka.publisher;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import com.example.coffeeorder.event.kafka.config.KafkaOrderEventProperties;
import com.example.coffeeorder.event.kafka.producer.OrderCompletedEventProducer;
import com.example.coffeeorder.event.outbox.entity.OutboxEvent;
import com.example.coffeeorder.event.outbox.service.OutboxEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OutboxPublisherService {

    private static final Logger log =
            LoggerFactory.getLogger(OutboxPublisherService.class);

    private final OutboxEventService outboxEventService;
    private final OrderCompletedEventProducer orderCompletedEventProducer;
    private final KafkaOrderEventProperties properties;
    private final Clock clock;

    public OutboxPublisherService(
            OutboxEventService outboxEventService,
            OrderCompletedEventProducer orderCompletedEventProducer,
            KafkaOrderEventProperties properties,
            Clock clock
    ) {
        this.outboxEventService = outboxEventService;
        this.orderCompletedEventProducer = orderCompletedEventProducer;
        this.properties = properties;
        this.clock = clock;
    }

    public int publishAvailableEvents() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<OutboxEvent> events =
                outboxEventService.reservePublishableEventsForPublish(
                        now,
                        properties.publisher()
                                .batchSize(),
                        properties.publisher()
                                .publishLeaseSeconds()
                );

        for (OutboxEvent event : events) {
            publish(event);
        }

        return events.size();
    }

    private void publish(OutboxEvent event) {
        try {
            orderCompletedEventProducer.send(event);
            outboxEventService.markPublished(event.getId());
        } catch (RuntimeException exception) {
            log.warn(
                    "Kafka 주문 완료 이벤트 발행에 실패했습니다. eventId={}, aggregateId={}",
                    event.getId(),
                    event.getAggregateId(),
                    exception
            );
            outboxEventService.markPublishFailed(
                    event.getId(),
                    exception.getMessage()
            );
        }
    }
}
