package com.example.coffeeorder.event.metrics;

import com.example.coffeeorder.event.entity.ExternalOrderEventStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class ExternalOrderEventMetricsRecorder {

    public static final String EXTERNAL_ORDER_EVENT_REQUESTS_METRIC =
            "coffee_order_external_order_event_requests";

    private final Counter successCounter;
    private final Counter failureCounter;
    private final Counter timeoutCounter;

    public ExternalOrderEventMetricsRecorder(MeterRegistry meterRegistry) {
        this.successCounter = counter(
                meterRegistry,
                "success"
        );
        this.failureCounter = counter(
                meterRegistry,
                "failure"
        );
        this.timeoutCounter = counter(
                meterRegistry,
                "timeout"
        );
    }

    public void record(ExternalOrderEventStatus status) {
        if (status == ExternalOrderEventStatus.SUCCESS) {
            successCounter.increment();
            return;
        }

        if (status == ExternalOrderEventStatus.TIMEOUT) {
            timeoutCounter.increment();
            return;
        }

        failureCounter.increment();
    }

    private Counter counter(
            MeterRegistry meterRegistry,
            String result
    ) {
        return Counter.builder(EXTERNAL_ORDER_EVENT_REQUESTS_METRIC)
                .tag(
                        "result",
                        result
                )
                .description("External order event API request result count")
                .register(meterRegistry);
    }
}
