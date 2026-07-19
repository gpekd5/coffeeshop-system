package com.example.coffeeorder.event.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.example.coffeeorder.event.kafka.consumer.OrderCompletedEventConsumer;
import com.example.coffeeorder.event.kafka.consumer.OrderCompletedEventProcessor;
import com.example.coffeeorder.event.kafka.consumer.service.DeadLetterOrderEventService;
import com.example.coffeeorder.event.kafka.publisher.OutboxPublisherScheduler;
import com.example.coffeeorder.event.kafka.publisher.OutboxPublisherService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class KafkaOrderEventProfileConditionTest {

    @Test
    void Kafka_Publisher는_활성화_설정이_true일_때만_스케줄러를_등록한다() {
        publisherContextRunner()
                .withPropertyValues("app.kafka-order-event.publisher.enabled=true")
                .run(context -> assertThat(context)
                        .hasSingleBean(OutboxPublisherScheduler.class));
    }

    @Test
    void Kafka_Publisher는_활성화_설정이_없으면_스케줄러를_등록하지_않는다() {
        publisherContextRunner()
                .run(context -> assertThat(context)
                        .doesNotHaveBean(OutboxPublisherScheduler.class));
    }

    @Test
    void Kafka_Consumer는_활성화_설정이_true일_때만_리스너를_등록한다() {
        consumerContextRunner()
                .withPropertyValues("app.kafka-order-event.consumer.enabled=true")
                .run(context -> assertThat(context)
                        .hasSingleBean(OrderCompletedEventConsumer.class));
    }

    @Test
    void Kafka_Consumer는_활성화_설정이_없으면_리스너를_등록하지_않는다() {
        consumerContextRunner()
                .run(context -> assertThat(context)
                        .doesNotHaveBean(OrderCompletedEventConsumer.class));
    }

    private ApplicationContextRunner publisherContextRunner() {
        return new ApplicationContextRunner()
                .withBean(
                        OutboxPublisherService.class,
                        () -> mock(OutboxPublisherService.class)
                )
                .withUserConfiguration(OutboxPublisherScheduler.class);
    }

    private ApplicationContextRunner consumerContextRunner() {
        return new ApplicationContextRunner()
                .withBean(
                        OrderCompletedEventProcessor.class,
                        () -> mock(OrderCompletedEventProcessor.class)
                )
                .withBean(
                        DeadLetterOrderEventService.class,
                        () -> mock(DeadLetterOrderEventService.class)
                )
                .withUserConfiguration(OrderCompletedEventConsumer.class);
    }
}
