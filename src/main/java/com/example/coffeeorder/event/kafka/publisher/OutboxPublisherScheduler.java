package com.example.coffeeorder.event.kafka.publisher;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "app.kafka-order-event.publisher.enabled",
        havingValue = "true"
)
public class OutboxPublisherScheduler {

    private final OutboxPublisherService outboxPublisherService;

    public OutboxPublisherScheduler(OutboxPublisherService outboxPublisherService) {
        this.outboxPublisherService = outboxPublisherService;
    }

    @Scheduled(fixedDelayString = "${app.kafka-order-event.publisher.fixed-delay-millis}")
    public void publish() {
        outboxPublisherService.publishAvailableEvents();
    }
}
