package com.example.coffeeorder.event.kafka.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@EnableConfigurationProperties(KafkaOrderEventProperties.class)
public class KafkaOrderEventConfig {

    @Bean
    DefaultErrorHandler orderCompletedEventErrorHandler(
            KafkaTemplate<Object, Object> kafkaTemplate,
            KafkaOrderEventProperties properties
    ) {
        FixedBackOff backOff = new FixedBackOff(
                properties.consumer()
                        .retryIntervalMillis(),
                Math.max(
                        0,
                        properties.consumer()
                                .maxAttempts() - 1
                )
        );
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(
                        kafkaTemplate,
                        (record, exception) -> new TopicPartition(
                                properties.deadLetterTopic(),
                                record.partition()
                        )
                );

        return new DefaultErrorHandler(
                recoverer,
                backOff
        );
    }
}
