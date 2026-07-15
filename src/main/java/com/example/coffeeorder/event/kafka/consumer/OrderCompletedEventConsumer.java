package com.example.coffeeorder.event.kafka.consumer;

import java.nio.charset.StandardCharsets;

import com.example.coffeeorder.event.kafka.consumer.service.DeadLetterOrderEventService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "app.kafka-order-event.consumer.enabled",
        havingValue = "true"
)
public class OrderCompletedEventConsumer {

    private static final String EMPTY_FAILURE_REASON = "unknown";

    private final OrderCompletedEventProcessor orderCompletedEventProcessor;
    private final DeadLetterOrderEventService deadLetterOrderEventService;

    public OrderCompletedEventConsumer(
            OrderCompletedEventProcessor orderCompletedEventProcessor,
            DeadLetterOrderEventService deadLetterOrderEventService
    ) {
        this.orderCompletedEventProcessor = orderCompletedEventProcessor;
        this.deadLetterOrderEventService = deadLetterOrderEventService;
    }

    @KafkaListener(
            topics = "${app.kafka-order-event.topic}",
            groupId = "${app.kafka-order-event.consumer.group-id}"
    )
    public void consume(ConsumerRecord<String, String> record) {
        orderCompletedEventProcessor.process(
                record.topic(),
                record.partition(),
                record.offset(),
                record.key(),
                record.value()
        );
    }

    @KafkaListener(
            topics = "${app.kafka-order-event.dead-letter-topic}",
            groupId = "${app.kafka-order-event.consumer.group-id}.dlt"
    )
    public void consumeDeadLetter(ConsumerRecord<String, String> record) {
        deadLetterOrderEventService.record(
                record.key(),
                originalTopic(record),
                record.topic(),
                record.partition(),
                record.offset(),
                record.value(),
                failureReason(record)
        );
    }

    private String originalTopic(ConsumerRecord<String, String> record) {
        Header header = record.headers()
                .lastHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC);

        if (header == null) {
            return record.topic();
        }

        return new String(
                header.value(),
                StandardCharsets.UTF_8
        );
    }

    private String failureReason(ConsumerRecord<String, String> record) {
        Header header = record.headers()
                .lastHeader(KafkaHeaders.DLT_EXCEPTION_MESSAGE);

        if (header == null) {
            return EMPTY_FAILURE_REASON;
        }

        return new String(
                header.value(),
                StandardCharsets.UTF_8
        );
    }
}
