package com.example.coffeeorder.event.outbox.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.example.coffeeorder.event.outbox.entity.OutboxEvent;
import com.example.coffeeorder.event.outbox.entity.OutboxStatus;
import com.example.coffeeorder.event.outbox.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class OutboxEventServiceIntegrationTest {

    @Autowired
    private OutboxEventService outboxEventService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
    }

    @Test
    void 발행_가능한_PENDING과_재시도_시각이_지난_FAILED_이벤트를_조회한다() {
        LocalDateTime now = LocalDateTime.of(
                2026,
                7,
                15,
                15,
                0
        );
        OutboxEvent pending = outboxEventRepository.saveAndFlush(
                이벤트를_생성한다(1L)
        );
        OutboxEvent dueFailed = 이벤트를_생성한다(2L);
        dueFailed.markPublishFailed(
                "temporary error",
                now.minusSeconds(1)
        );
        outboxEventRepository.saveAndFlush(dueFailed);

        OutboxEvent futureFailed = 이벤트를_생성한다(3L);
        futureFailed.markPublishFailed(
                "temporary error",
                now.plusMinutes(5)
        );
        outboxEventRepository.saveAndFlush(futureFailed);

        OutboxEvent published = 이벤트를_생성한다(4L);
        published.markPublished(now.minusMinutes(1));
        outboxEventRepository.saveAndFlush(published);

        List<OutboxEvent> events =
                outboxEventService.findPublishableEventsForUpdate(
                        now,
                        10
                );

        assertThat(events)
                .extracting(OutboxEvent::getId)
                .containsExactly(
                        pending.getId(),
                        dueFailed.getId()
                );
    }

    @Test
    void 발행_실패_이벤트는_재시도_대상으로_조회하고_수동_재처리할_수_있다() {
        OutboxEvent event = outboxEventRepository.saveAndFlush(
                이벤트를_생성한다(10L)
        );

        outboxEventService.markPublishFailed(
                event.getId(),
                "Kafka publish failed"
        );

        OutboxEvent failedEvent = outboxEventRepository.findById(event.getId())
                .orElseThrow();

        assertThat(failedEvent.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(failedEvent.getRetryCount()).isEqualTo(1);
        assertThat(failedEvent.getNextRetryAt()).isNotNull();
        assertThat(outboxEventService.findFailedEvents())
                .extracting(OutboxEvent::getId)
                .containsExactly(event.getId());

        outboxEventService.resetFailedEventForRetry(event.getId());

        OutboxEvent retryEvent = outboxEventRepository.findById(event.getId())
                .orElseThrow();

        assertThat(retryEvent.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(retryEvent.getRetryCount()).isEqualTo(1);
        assertThat(retryEvent.isPublishable(LocalDateTime.now()
                .plusSeconds(1))).isTrue();

        outboxEventService.markPublished(event.getId());

        OutboxEvent publishedEvent = outboxEventRepository.findById(event.getId())
                .orElseThrow();

        assertThat(publishedEvent.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(publishedEvent.getPublishedAt()).isNotNull();
        assertThat(publishedEvent.getLastError()).isNull();
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
}
