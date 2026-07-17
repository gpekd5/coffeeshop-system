package com.example.coffeeorder.event.kafka.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.example.coffeeorder.event.client.ExternalOrderEventClient;
import com.example.coffeeorder.event.client.ExternalOrderEventSendResult;
import com.example.coffeeorder.event.dto.OrderCompletedEventRequest;
import com.example.coffeeorder.event.entity.ExternalOrderEventStatus;
import com.example.coffeeorder.event.kafka.consumer.entity.KafkaEventProcessingStatus;
import com.example.coffeeorder.event.kafka.consumer.entity.ProcessedKafkaEvent;
import com.example.coffeeorder.event.kafka.consumer.repository.ProcessedKafkaEventRepository;
import com.example.coffeeorder.event.metrics.KafkaConsumerMetricsRecorder;
import com.example.coffeeorder.event.outbox.dto.OrderCompletedOutboxPayload;
import com.example.coffeeorder.menu.entity.MenuCategory;
import com.example.coffeeorder.order.dto.response.OrderItemResponse;
import com.example.coffeeorder.order.entity.OrderChannel;
import com.example.coffeeorder.payment.entity.PaymentMethod;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import(OrderCompletedEventProcessorIntegrationTest.TestConfig.class)
class OrderCompletedEventProcessorIntegrationTest {

    private static final String TOPIC = "order.completed";

    @Autowired
    private OrderCompletedEventProcessor orderCompletedEventProcessor;

    @Autowired
    private ProcessedKafkaEventRepository processedKafkaEventRepository;

    @Autowired
    private RecordingExternalOrderEventClient externalOrderEventClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        processedKafkaEventRepository.deleteAll();
        externalOrderEventClient.reset();
    }

    @Test
    void 주문_완료_이벤트를_처리하면_외부_API를_호출하고_COMPLETED로_기록한다()
            throws Exception {
        String eventId = UUID.randomUUID()
                .toString();
        String payload = payload(eventId);
        double successCount = kafkaConsumerCounter(
                KafkaConsumerMetricsRecorder.RESULT_SUCCESS
        );

        orderCompletedEventProcessor.process(
                TOPIC,
                0,
                1L,
                eventId,
                payload
        );

        ProcessedKafkaEvent event = processedKafkaEventRepository
                .findById(eventId)
                .orElseThrow();

        assertThat(externalOrderEventClient.requestCount()).isEqualTo(1);
        assertThat(externalOrderEventClient.lastRequest()
                .eventId()).isEqualTo(eventId);
        assertThat(event.getStatus()).isEqualTo(KafkaEventProcessingStatus.COMPLETED);
        assertThat(event.getAttemptCount()).isEqualTo(1);
        assertThat(event.getProcessedAt()).isNotNull();
        assertThat(kafkaConsumerCounter(
                KafkaConsumerMetricsRecorder.RESULT_SUCCESS
        )).isEqualTo(successCount + 1.0);
    }

    @Test
    void 이미_COMPLETED로_기록된_이벤트는_중복_수신해도_외부_API를_재호출하지_않는다()
            throws Exception {
        String eventId = UUID.randomUUID()
                .toString();
        String payload = payload(eventId);
        double successCount = kafkaConsumerCounter(
                KafkaConsumerMetricsRecorder.RESULT_SUCCESS
        );
        double duplicateSkipCount = kafkaConsumerCounter(
                KafkaConsumerMetricsRecorder.RESULT_DUPLICATE_SKIP
        );

        orderCompletedEventProcessor.process(
                TOPIC,
                0,
                1L,
                eventId,
                payload
        );
        orderCompletedEventProcessor.process(
                TOPIC,
                0,
                2L,
                eventId,
                payload
        );

        ProcessedKafkaEvent event = processedKafkaEventRepository
                .findById(eventId)
                .orElseThrow();

        assertThat(externalOrderEventClient.requestCount()).isEqualTo(1);
        assertThat(event.getStatus()).isEqualTo(KafkaEventProcessingStatus.COMPLETED);
        assertThat(event.getAttemptCount()).isEqualTo(1);
        assertThat(event.getKafkaOffset()).isEqualTo(1L);
        assertThat(kafkaConsumerCounter(
                KafkaConsumerMetricsRecorder.RESULT_SUCCESS
        )).isEqualTo(successCount + 1.0);
        assertThat(kafkaConsumerCounter(
                KafkaConsumerMetricsRecorder.RESULT_DUPLICATE_SKIP
        )).isEqualTo(duplicateSkipCount + 1.0);
    }

    @Test
    void 외부_API_호출이_실패한_이벤트는_FAILED로_기록하고_다음_수신에서_재처리한다()
            throws Exception {
        String eventId = UUID.randomUUID()
                .toString();
        String payload = payload(eventId);
        double successCount = kafkaConsumerCounter(
                KafkaConsumerMetricsRecorder.RESULT_SUCCESS
        );
        double failureCount = kafkaConsumerCounter(
                KafkaConsumerMetricsRecorder.RESULT_FAILURE
        );
        externalOrderEventClient.enqueue(
                ExternalOrderEventSendResult.failed(
                        null,
                        "temporary failure",
                        LocalDateTime.now()
                )
        );
        externalOrderEventClient.enqueue(
                ExternalOrderEventSendResult.success(
                        200,
                        LocalDateTime.now()
                )
        );

        assertThatThrownBy(() -> orderCompletedEventProcessor.process(
                TOPIC,
                0,
                1L,
                eventId,
                payload
        )).isInstanceOf(KafkaOrderEventProcessingException.class);

        ProcessedKafkaEvent failedEvent = processedKafkaEventRepository
                .findById(eventId)
                .orElseThrow();

        assertThat(failedEvent.getStatus()).isEqualTo(KafkaEventProcessingStatus.FAILED);
        assertThat(failedEvent.getAttemptCount()).isEqualTo(1);
        assertThat(kafkaConsumerCounter(
                KafkaConsumerMetricsRecorder.RESULT_FAILURE
        )).isEqualTo(failureCount + 1.0);

        orderCompletedEventProcessor.process(
                TOPIC,
                0,
                2L,
                eventId,
                payload
        );

        ProcessedKafkaEvent completedEvent = processedKafkaEventRepository
                .findById(eventId)
                .orElseThrow();

        assertThat(externalOrderEventClient.requestCount()).isEqualTo(2);
        assertThat(completedEvent.getStatus()).isEqualTo(KafkaEventProcessingStatus.COMPLETED);
        assertThat(completedEvent.getAttemptCount()).isEqualTo(2);
        assertThat(completedEvent.getKafkaOffset()).isEqualTo(2L);
        assertThat(kafkaConsumerCounter(
                KafkaConsumerMetricsRecorder.RESULT_SUCCESS
        )).isEqualTo(successCount + 1.0);
    }

    @Test
    void 외부_API_Timeout_이벤트는_FAILED로_기록하고_다음_수신에서_재처리한다()
            throws Exception {
        String eventId = UUID.randomUUID()
                .toString();
        String payload = payload(eventId);
        double successCount = kafkaConsumerCounter(
                KafkaConsumerMetricsRecorder.RESULT_SUCCESS
        );
        double failureCount = kafkaConsumerCounter(
                KafkaConsumerMetricsRecorder.RESULT_FAILURE
        );
        externalOrderEventClient.enqueue(
                ExternalOrderEventSendResult.timeout(
                        "read timeout",
                        LocalDateTime.now()
                )
        );
        externalOrderEventClient.enqueue(
                ExternalOrderEventSendResult.success(
                        200,
                        LocalDateTime.now()
                )
        );

        assertThatThrownBy(() -> orderCompletedEventProcessor.process(
                TOPIC,
                0,
                1L,
                eventId,
                payload
        ))
                .isInstanceOf(KafkaOrderEventProcessingException.class)
                .hasMessageContaining("status=TIMEOUT")
                .hasMessageContaining("read timeout");

        ProcessedKafkaEvent failedEvent = processedKafkaEventRepository
                .findById(eventId)
                .orElseThrow();

        assertThat(failedEvent.getStatus()).isEqualTo(KafkaEventProcessingStatus.FAILED);
        assertThat(failedEvent.getAttemptCount()).isEqualTo(1);
        assertThat(failedEvent.getLastError()).contains("status=TIMEOUT");
        assertThat(kafkaConsumerCounter(
                KafkaConsumerMetricsRecorder.RESULT_FAILURE
        )).isEqualTo(failureCount + 1.0);

        orderCompletedEventProcessor.process(
                TOPIC,
                0,
                2L,
                eventId,
                payload
        );

        ProcessedKafkaEvent completedEvent = processedKafkaEventRepository
                .findById(eventId)
                .orElseThrow();

        assertThat(externalOrderEventClient.requestCount()).isEqualTo(2);
        assertThat(completedEvent.getStatus()).isEqualTo(KafkaEventProcessingStatus.COMPLETED);
        assertThat(completedEvent.getAttemptCount()).isEqualTo(2);
        assertThat(completedEvent.getKafkaOffset()).isEqualTo(2L);
        assertThat(kafkaConsumerCounter(
                KafkaConsumerMetricsRecorder.RESULT_SUCCESS
        )).isEqualTo(successCount + 1.0);
    }

    @Test
    void PROCESSING_lease가_유효한_이벤트는_중복_수신해도_외부_API를_호출하지_않는다()
            throws Exception {
        String eventId = UUID.randomUUID()
                .toString();
        String payload = payload(eventId);
        double duplicateSkipCount = kafkaConsumerCounter(
                KafkaConsumerMetricsRecorder.RESULT_DUPLICATE_SKIP
        );
        processedKafkaEventRepository.saveAndFlush(ProcessedKafkaEvent.start(
                eventId,
                "ORDER_COMPLETED",
                TOPIC,
                0,
                1L,
                LocalDateTime.now()
                        .plusMinutes(1)
        ));

        orderCompletedEventProcessor.process(
                TOPIC,
                0,
                2L,
                eventId,
                payload
        );

        ProcessedKafkaEvent event = processedKafkaEventRepository
                .findById(eventId)
                .orElseThrow();

        assertThat(externalOrderEventClient.requestCount()).isZero();
        assertThat(event.getStatus()).isEqualTo(KafkaEventProcessingStatus.PROCESSING);
        assertThat(event.getAttemptCount()).isEqualTo(1);
        assertThat(event.getKafkaOffset()).isEqualTo(1L);
        assertThat(kafkaConsumerCounter(
                KafkaConsumerMetricsRecorder.RESULT_DUPLICATE_SKIP
        )).isEqualTo(duplicateSkipCount + 1.0);
    }

    @Test
    void PROCESSING_저장_후_종료되어_lease가_만료된_이벤트는_재처리한다()
            throws Exception {
        String eventId = UUID.randomUUID()
                .toString();
        String payload = payload(eventId);
        double successCount = kafkaConsumerCounter(
                KafkaConsumerMetricsRecorder.RESULT_SUCCESS
        );
        processedKafkaEventRepository.saveAndFlush(ProcessedKafkaEvent.start(
                eventId,
                "ORDER_COMPLETED",
                TOPIC,
                0,
                1L,
                LocalDateTime.now()
                        .minusSeconds(1)
        ));

        orderCompletedEventProcessor.process(
                TOPIC,
                0,
                2L,
                eventId,
                payload
        );

        ProcessedKafkaEvent event = processedKafkaEventRepository
                .findById(eventId)
                .orElseThrow();

        assertThat(externalOrderEventClient.requestCount()).isEqualTo(1);
        assertThat(event.getStatus()).isEqualTo(KafkaEventProcessingStatus.COMPLETED);
        assertThat(event.getAttemptCount()).isEqualTo(2);
        assertThat(event.getKafkaOffset()).isEqualTo(2L);
        assertThat(event.getProcessingDeadlineAt()).isNull();
        assertThat(event.getProcessedAt()).isNotNull();
        assertThat(kafkaConsumerCounter(
                KafkaConsumerMetricsRecorder.RESULT_SUCCESS
        )).isEqualTo(successCount + 1.0);
    }

    private String payload(String eventId) throws Exception {
        return objectMapper.writeValueAsString(new OrderCompletedOutboxPayload(
                eventId,
                "ORDER_COMPLETED",
                "ORDER",
                1L,
                1L,
                "ORD-20260715-000001",
                1L,
                OrderChannel.WEB_CART,
                List.of(new OrderItemResponse(
                        1L,
                        1L,
                        "아메리카노",
                        MenuCategory.COFFEE,
                        4_500L,
                        1,
                        4_500L
                )),
                4_500L,
                PaymentMethod.POINT,
                LocalDateTime.now(),
                LocalDateTime.now()
        ));
    }

    private double kafkaConsumerCounter(String result) {
        Counter counter = meterRegistry.find(
                        KafkaConsumerMetricsRecorder.KAFKA_CONSUMER_EVENTS_METRIC
                )
                .tag(
                        "result",
                        result
                )
                .counter();

        if (counter == null) {
            return 0.0;
        }

        return counter.count();
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        RecordingExternalOrderEventClient externalOrderEventClient() {
            return new RecordingExternalOrderEventClient();
        }
    }

    static class RecordingExternalOrderEventClient implements ExternalOrderEventClient {

        private final AtomicInteger requestCount = new AtomicInteger(0);
        private final AtomicReference<OrderCompletedEventRequest> lastRequest =
                new AtomicReference<>();
        private final ConcurrentLinkedQueue<ExternalOrderEventSendResult> results =
                new ConcurrentLinkedQueue<>();

        @Override
        public ExternalOrderEventSendResult send(OrderCompletedEventRequest request) {
            requestCount.incrementAndGet();
            lastRequest.set(request);

            ExternalOrderEventSendResult result = results.poll();

            if (result != null) {
                return result;
            }

            return ExternalOrderEventSendResult.success(
                    200,
                    LocalDateTime.now()
            );
        }

        void enqueue(ExternalOrderEventSendResult result) {
            results.add(result);
        }

        int requestCount() {
            return requestCount.get();
        }

        OrderCompletedEventRequest lastRequest() {
            return lastRequest.get();
        }

        void reset() {
            requestCount.set(0);
            lastRequest.set(null);
            results.clear();
        }
    }
}
