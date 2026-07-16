package com.example.coffeeorder.event.kafka.producer;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.example.coffeeorder.event.kafka.config.KafkaOrderEventProperties;
import com.example.coffeeorder.event.outbox.entity.OutboxEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class SpringKafkaOrderCompletedEventProducer
        implements OrderCompletedEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaOrderEventProperties properties;

    public SpringKafkaOrderCompletedEventProducer(
            KafkaTemplate<String, String> kafkaTemplate,
            KafkaOrderEventProperties properties
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    @Override
    public void send(OutboxEvent event) {
        try {
            kafkaTemplate.send(
                            properties.topic(),
                            event.getId(),
                            event.getPayload()
                    )
                    .get(
                            sendTimeout().toMillis(),
                            TimeUnit.MILLISECONDS
                    );
        } catch (InterruptedException exception) {
            Thread.currentThread()
                    .interrupt();

            throw new KafkaPublishException(
                    "Kafka 발행이 인터럽트되었습니다.",
                    exception
            );
        } catch (ExecutionException | TimeoutException exception) {
            throw new KafkaPublishException(
                    "Kafka 발행에 실패했습니다.",
                    exception
            );
        }
    }

    private Duration sendTimeout() {
        return Duration.ofMillis(properties.producer()
                .sendTimeoutMillis());
    }
}
