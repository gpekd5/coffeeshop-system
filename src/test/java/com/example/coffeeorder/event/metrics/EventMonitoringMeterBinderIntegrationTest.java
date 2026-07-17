package com.example.coffeeorder.event.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;

import com.example.coffeeorder.event.kafka.consumer.entity.DeadLetterOrderEvent;
import com.example.coffeeorder.event.kafka.consumer.repository.DeadLetterOrderEventRepository;
import com.example.coffeeorder.event.outbox.entity.OutboxEvent;
import com.example.coffeeorder.event.outbox.entity.OutboxStatus;
import com.example.coffeeorder.event.outbox.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class EventMonitoringMeterBinderIntegrationTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private DeadLetterOrderEventRepository deadLetterOrderEventRepository;

    @Autowired
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        deadLetterOrderEventRepository.deleteAll();
        outboxEventRepository.deleteAll();
    }

    @Test
    void Outbox_상태별_이벤트_수를_Gauge로_확인할_수_있다() {
        OutboxEvent pendingEvent = outboxEvent(1L);
        OutboxEvent failedEvent = outboxEvent(2L);
        OutboxEvent publishedEvent = outboxEvent(3L);
        failedEvent.markPublishFailed(
                "Kafka unavailable",
                LocalDateTime.now()
        );
        publishedEvent.markPublished(LocalDateTime.now());

        outboxEventRepository.save(pendingEvent);
        outboxEventRepository.save(failedEvent);
        outboxEventRepository.saveAndFlush(publishedEvent);

        assertThat(outboxEventGauge(OutboxStatus.PENDING).value()).isEqualTo(1.0);
        assertThat(outboxEventGauge(OutboxStatus.FAILED).value()).isEqualTo(1.0);
        assertThat(outboxEventGauge(OutboxStatus.PUBLISHED).value()).isEqualTo(1.0);
    }

    @Test
    void 가장_오래된_PENDING_이벤트_age와_Dead_Letter_수를_Gauge로_확인할_수_있다() {
        outboxEventRepository.saveAndFlush(outboxEvent(1L));
        deadLetterOrderEventRepository.saveAndFlush(DeadLetterOrderEvent.of(
                UUID.randomUUID()
                        .toString(),
                "order.completed",
                "order.completed.DLT",
                0,
                10L,
                "{\"eventId\":\"event-1\"}",
                "external API failed",
                LocalDateTime.now()
        ));

        Gauge oldestPendingAgeGauge = meterRegistry.get(
                        EventMonitoringMeterBinder.OUTBOX_OLDEST_PENDING_AGE_SECONDS_METRIC
                )
                .gauge();
        Gauge deadLetterGauge = meterRegistry.get(
                        EventMonitoringMeterBinder.DEAD_LETTER_ORDER_EVENTS_METRIC
                )
                .gauge();

        assertThat(oldestPendingAgeGauge.value()).isGreaterThanOrEqualTo(0.0);
        assertThat(deadLetterGauge.value()).isEqualTo(1.0);
    }

    private OutboxEvent outboxEvent(Long orderId) {
        return OutboxEvent.orderCompleted(
                UUID.randomUUID()
                        .toString(),
                orderId,
                "{\"eventType\":\"ORDER_COMPLETED\"}"
        );
    }

    private Gauge outboxEventGauge(OutboxStatus status) {
        return meterRegistry.get(EventMonitoringMeterBinder.OUTBOX_EVENTS_METRIC)
                .tag(
                        "status",
                        status.name()
                )
                .gauge();
    }
}
