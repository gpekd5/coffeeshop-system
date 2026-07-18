package com.example.coffeeorder.event.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.coffeeorder.event.entity.ExternalOrderEventStatus;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class ExternalOrderEventMetricsRecorderTest {

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final ExternalOrderEventMetricsRecorder metricsRecorder =
            new ExternalOrderEventMetricsRecorder(meterRegistry);

    @Test
    void 외부_주문_이벤트_API_호출_결과를_라벨별_Counter로_기록한다() {
        metricsRecorder.record(ExternalOrderEventStatus.SUCCESS);
        metricsRecorder.record(ExternalOrderEventStatus.FAILED);
        metricsRecorder.record(ExternalOrderEventStatus.TIMEOUT);

        assertThat(counter("success")).isEqualTo(1.0);
        assertThat(counter("failure")).isEqualTo(1.0);
        assertThat(counter("timeout")).isEqualTo(1.0);
    }

    private double counter(String result) {
        return meterRegistry.get(
                        ExternalOrderEventMetricsRecorder
                                .EXTERNAL_ORDER_EVENT_REQUESTS_METRIC
                )
                .tag(
                        "result",
                        result
                )
                .counter()
                .count();
    }
}
