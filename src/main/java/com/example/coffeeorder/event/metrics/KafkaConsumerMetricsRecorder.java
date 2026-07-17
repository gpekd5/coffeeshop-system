package com.example.coffeeorder.event.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class KafkaConsumerMetricsRecorder {

    public static final String KAFKA_CONSUMER_EVENTS_METRIC =
            "coffee_order_kafka_consumer_events";
    public static final String RESULT_SUCCESS = "success";
    public static final String RESULT_FAILURE = "failure";
    public static final String RESULT_DUPLICATE_SKIP = "duplicate_skip";

    private final Counter successCounter;
    private final Counter failureCounter;
    private final Counter duplicateSkipCounter;

    public KafkaConsumerMetricsRecorder(MeterRegistry meterRegistry) {
        this.successCounter = counter(
                meterRegistry,
                RESULT_SUCCESS
        );
        this.failureCounter = counter(
                meterRegistry,
                RESULT_FAILURE
        );
        this.duplicateSkipCounter = counter(
                meterRegistry,
                RESULT_DUPLICATE_SKIP
        );
    }

    public void recordSuccess() {
        successCounter.increment();
    }

    public void recordFailure() {
        failureCounter.increment();
    }

    public void recordDuplicateSkip() {
        duplicateSkipCounter.increment();
    }

    private Counter counter(
            MeterRegistry meterRegistry,
            String result
    ) {
        return Counter.builder(KAFKA_CONSUMER_EVENTS_METRIC)
                .tag(
                        "result",
                        result
                )
                .description("Kafka order event consumer processing result count")
                .register(meterRegistry);
    }
}
