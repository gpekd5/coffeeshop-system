package com.example.coffeeorder.event.metrics;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

import com.example.coffeeorder.event.kafka.consumer.repository.DeadLetterOrderEventRepository;
import com.example.coffeeorder.event.outbox.entity.OutboxEvent;
import com.example.coffeeorder.event.outbox.entity.OutboxStatus;
import com.example.coffeeorder.event.outbox.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

@Component
public class EventMonitoringMeterBinder implements MeterBinder {

    public static final String OUTBOX_EVENTS_METRIC = "coffee_order_outbox_events";
    public static final String OUTBOX_OLDEST_PENDING_AGE_SECONDS_METRIC =
            "coffee_order_outbox_oldest_pending_age_seconds";
    public static final String DEAD_LETTER_ORDER_EVENTS_METRIC =
            "coffee_order_dead_letter_order_events";

    private final OutboxEventRepository outboxEventRepository;
    private final DeadLetterOrderEventRepository deadLetterOrderEventRepository;
    private final Clock clock;

    public EventMonitoringMeterBinder(
            OutboxEventRepository outboxEventRepository,
            DeadLetterOrderEventRepository deadLetterOrderEventRepository,
            Clock clock
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.deadLetterOrderEventRepository = deadLetterOrderEventRepository;
        this.clock = clock;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        for (OutboxStatus status : OutboxStatus.values()) {
            Gauge.builder(
                            OUTBOX_EVENTS_METRIC,
                            outboxEventRepository,
                            repository -> repository.countByStatus(status)
                    )
                    .tag(
                            "status",
                            status.name()
                    )
                    .description("Outbox event count by publication status")
                    .register(registry);
        }

        Gauge.builder(
                        OUTBOX_OLDEST_PENDING_AGE_SECONDS_METRIC,
                        this,
                        EventMonitoringMeterBinder::oldestPendingAgeSeconds
                )
                .description("Age in seconds of the oldest pending outbox event")
                .register(registry);

        Gauge.builder(
                        DEAD_LETTER_ORDER_EVENTS_METRIC,
                        deadLetterOrderEventRepository,
                        DeadLetterOrderEventRepository::count
                )
                .description("Dead letter order event count")
                .register(registry);
    }

    private double oldestPendingAgeSeconds() {
        return outboxEventRepository
                .findFirstByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING)
                .map(this::ageSeconds)
                .orElse(0.0);
    }

    private double ageSeconds(OutboxEvent event) {
        LocalDateTime createdAt = event.getCreatedAt();

        if (createdAt == null) {
            return 0.0;
        }

        long seconds = Duration.between(
                        createdAt,
                        LocalDateTime.now(clock)
                )
                .toSeconds();

        return Math.max(
                0,
                seconds
        );
    }
}
