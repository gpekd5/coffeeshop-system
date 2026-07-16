package com.example.coffeeorder.event.kafka.consumer;

import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;

import com.example.coffeeorder.event.kafka.consumer.service.DeadLetterOrderEventService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.KafkaHeaders;

@ExtendWith(MockitoExtension.class)
class OrderCompletedEventConsumerTest {

    @Mock
    private OrderCompletedEventProcessor orderCompletedEventProcessor;

    @Mock
    private DeadLetterOrderEventService deadLetterOrderEventService;

    @InjectMocks
    private OrderCompletedEventConsumer orderCompletedEventConsumer;

    @Test
    void 주문_완료_이벤트를_Processor에_위임한다() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "order.completed",
                0,
                10L,
                "event-1",
                "{}"
        );

        orderCompletedEventConsumer.consume(record);

        verify(orderCompletedEventProcessor).process(
                "order.completed",
                0,
                10L,
                "event-1",
                "{}"
        );
    }

    @Test
    void Dead_Letter_이벤트를_저장한다() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "order.completed.DLT",
                1,
                11L,
                "event-2",
                "{\"eventId\":\"event-2\"}"
        );
        record.headers()
                .add(
                        KafkaHeaders.DLT_ORIGINAL_TOPIC,
                        "order.completed".getBytes(StandardCharsets.UTF_8)
                );
        record.headers()
                .add(
                        KafkaHeaders.DLT_EXCEPTION_MESSAGE,
                        "external failure".getBytes(StandardCharsets.UTF_8)
                );

        orderCompletedEventConsumer.consumeDeadLetter(record);

        verify(deadLetterOrderEventService).record(
                "event-2",
                "order.completed",
                "order.completed.DLT",
                1,
                11L,
                "{\"eventId\":\"event-2\"}",
                "external failure"
        );
    }
}
