package com.example.coffeeorder.event.kafka.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.kafka-order-event")
public record KafkaOrderEventProperties(
        String topic,
        String deadLetterTopic,
        Producer producer,
        Publisher publisher,
        Consumer consumer
) {

    public record Producer(
            long sendTimeoutMillis
    ) {
    }

    public record Publisher(
            boolean enabled,
            int batchSize,
            long fixedDelayMillis,
            long publishLeaseSeconds
    ) {
    }

    public record Consumer(
            boolean enabled,
            String groupId,
            long maxAttempts,
            long retryIntervalMillis,
            long processingLeaseSeconds
    ) {
    }
}
