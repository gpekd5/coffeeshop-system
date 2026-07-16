package com.example.coffeeorder.event.kafka.publisher;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import com.example.coffeeorder.event.kafka.producer.OrderCompletedEventProducer;
import com.example.coffeeorder.event.outbox.entity.OutboxEvent;
import com.example.coffeeorder.event.outbox.entity.OutboxStatus;
import com.example.coffeeorder.event.outbox.repository.OutboxEventRepository;
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

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.kafka-order-event.publisher.batch-size=10",
        "app.kafka-order-event.publisher.publish-lease-seconds=30"
})
@Import(OutboxPublisherServiceIntegrationTest.TestConfig.class)
class OutboxPublisherServiceIntegrationTest {

    @Autowired
    private OutboxPublisherService outboxPublisherService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private RecordingOrderCompletedEventProducer producer;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        producer.reset();
    }

    @Test
    void PENDING_Outbox_이벤트를_Kafka로_발행하고_PUBLISHED로_변경한다() {
        OutboxEvent event = outboxEventRepository.saveAndFlush(
                이벤트를_생성한다(1L)
        );

        int publishedCount = outboxPublisherService.publishAvailableEvents();

        OutboxEvent publishedEvent = outboxEventRepository
                .findById(event.getId())
                .orElseThrow();

        assertThat(publishedCount).isEqualTo(1);
        assertThat(producer.sentEventIds()).containsExactly(event.getId());
        assertThat(publishedEvent.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(publishedEvent.getPublishedAt()).isNotNull();
        assertThat(publishedEvent.getNextRetryAt()).isNull();
    }

    @Test
    void Kafka_발행에_실패하면_Outbox_이벤트를_FAILED로_변경한다() {
        OutboxEvent event = outboxEventRepository.saveAndFlush(
                이벤트를_생성한다(2L)
        );
        producer.failWith(new RuntimeException("Kafka unavailable"));

        int publishedCount = outboxPublisherService.publishAvailableEvents();

        OutboxEvent failedEvent = outboxEventRepository.findById(event.getId())
                .orElseThrow();

        assertThat(publishedCount).isEqualTo(1);
        assertThat(failedEvent.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(failedEvent.getRetryCount()).isEqualTo(1);
        assertThat(failedEvent.getLastError()).contains("Kafka unavailable");
        assertThat(failedEvent.getNextRetryAt()).isNotNull();
    }

    @Test
    void 재시도_시각이_지난_FAILED_Outbox_이벤트를_재처리하면_PUBLISHED로_변경한다() {
        OutboxEvent event = 이벤트를_생성한다(3L);
        event.markPublishFailed(
                "Kafka unavailable",
                LocalDateTime.now()
                        .minusSeconds(1)
        );
        outboxEventRepository.saveAndFlush(event);

        int retriedPublishCount = outboxPublisherService.publishAvailableEvents();

        OutboxEvent publishedEvent = outboxEventRepository.findById(event.getId())
                .orElseThrow();

        assertThat(retriedPublishCount).isEqualTo(1);
        assertThat(producer.sentEventIds()).containsExactly(event.getId());
        assertThat(publishedEvent.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(publishedEvent.getRetryCount()).isEqualTo(1);
        assertThat(publishedEvent.getPublishedAt()).isNotNull();
    }

    private OutboxEvent 이벤트를_생성한다(Long orderId) {
        return OutboxEvent.orderCompleted(
                UUID.randomUUID()
                        .toString(),
                orderId,
                """
                        {"eventType":"ORDER_COMPLETED"}
                        """
        );
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        RecordingOrderCompletedEventProducer orderCompletedEventProducer() {
            return new RecordingOrderCompletedEventProducer();
        }
    }

    static class RecordingOrderCompletedEventProducer
            implements OrderCompletedEventProducer {

        private final List<String> sentEventIds = new CopyOnWriteArrayList<>();
        private final AtomicReference<RuntimeException> failure =
                new AtomicReference<>();

        @Override
        public void send(OutboxEvent event) {
            RuntimeException exception = failure.get();

            if (exception != null) {
                throw exception;
            }

            sentEventIds.add(event.getId());
        }

        void failWith(RuntimeException exception) {
            failure.set(exception);
        }

        List<String> sentEventIds() {
            return sentEventIds;
        }

        void reset() {
            sentEventIds.clear();
            failure.set(null);
        }
    }
}
