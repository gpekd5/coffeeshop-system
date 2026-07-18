package com.example.coffeeorder.event.kafka.consumer;

import com.example.coffeeorder.event.client.ExternalOrderEventClient;
import com.example.coffeeorder.event.client.ExternalOrderEventSendResult;
import com.example.coffeeorder.event.dto.OrderCompletedEventItemRequest;
import com.example.coffeeorder.event.dto.OrderCompletedEventRequest;
import com.example.coffeeorder.event.entity.ExternalOrderEventStatus;
import com.example.coffeeorder.event.kafka.consumer.service.ProcessedKafkaEventService;
import com.example.coffeeorder.event.metrics.KafkaConsumerMetricsRecorder;
import com.example.coffeeorder.event.outbox.dto.OrderCompletedOutboxPayload;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class OrderCompletedEventProcessor {

    private final ObjectMapper objectMapper;
    private final ProcessedKafkaEventService processedKafkaEventService;
    private final ExternalOrderEventClient externalOrderEventClient;
    private final KafkaConsumerMetricsRecorder kafkaConsumerMetricsRecorder;

    public OrderCompletedEventProcessor(
            ObjectMapper objectMapper,
            ProcessedKafkaEventService processedKafkaEventService,
            ExternalOrderEventClient externalOrderEventClient,
            KafkaConsumerMetricsRecorder kafkaConsumerMetricsRecorder
    ) {
        this.objectMapper = objectMapper;
        this.processedKafkaEventService = processedKafkaEventService;
        this.externalOrderEventClient = externalOrderEventClient;
        this.kafkaConsumerMetricsRecorder = kafkaConsumerMetricsRecorder;
    }

    public void process(
            String topic,
            Integer kafkaPartition,
            Long kafkaOffset,
            String eventId,
            String payload
    ) {
        OrderCompletedOutboxPayload outboxPayload;

        try {
            outboxPayload = deserialize(payload);
        } catch (RuntimeException exception) {
            kafkaConsumerMetricsRecorder.recordFailure();
            throw exception;
        }

        String processingEventId = resolveEventId(
                eventId,
                outboxPayload
        );
        boolean shouldProcess = processedKafkaEventService.beginProcessing(
                processingEventId,
                outboxPayload.eventType(),
                topic,
                kafkaPartition,
                kafkaOffset
        );

        if (!shouldProcess) {
            kafkaConsumerMetricsRecorder.recordDuplicateSkip();
            return;
        }

        try {
            ExternalOrderEventSendResult result =
                    externalOrderEventClient.send(toRequest(outboxPayload));

            if (result.status() == ExternalOrderEventStatus.SUCCESS) {
                processedKafkaEventService.complete(processingEventId);
                kafkaConsumerMetricsRecorder.recordSuccess();
                return;
            }

            throw new KafkaOrderEventProcessingException(
                    "외부 주문 이벤트 전송에 실패했습니다. eventId="
                            + processingEventId
                            + ", status="
                            + result.status()
                            + ", errorMessage="
                            + result.errorMessage()
            );
        } catch (RuntimeException exception) {
            processedKafkaEventService.fail(
                    processingEventId,
                    exception.getMessage()
            );
            kafkaConsumerMetricsRecorder.recordFailure();

            throw exception;
        }
    }

    private OrderCompletedOutboxPayload deserialize(String payload) {
        try {
            return objectMapper.readValue(
                    payload,
                    OrderCompletedOutboxPayload.class
            );
        } catch (Exception exception) {
            throw new KafkaOrderEventProcessingException(
                    "Kafka 주문 완료 이벤트 역직렬화에 실패했습니다.",
                    exception
            );
        }
    }

    private String resolveEventId(
            String eventId,
            OrderCompletedOutboxPayload outboxPayload
    ) {
        if (eventId != null && !eventId.isBlank()) {
            return eventId;
        }

        return outboxPayload.eventId();
    }

    private OrderCompletedEventRequest toRequest(
            OrderCompletedOutboxPayload payload
    ) {
        return new OrderCompletedEventRequest(
                payload.eventId(),
                payload.eventType(),
                payload.orderId(),
                payload.orderNumber(),
                payload.memberId(),
                payload.orderChannel(),
                payload.items()
                        .stream()
                        .map(OrderCompletedEventItemRequest::from)
                        .toList(),
                payload.totalAmount(),
                payload.paymentMethod(),
                payload.orderedAt()
        );
    }
}
