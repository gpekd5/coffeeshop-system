package com.example.coffeeorder.event.kafka.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.coffeeorder.event.client.ExternalOrderEventProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class ProdExternalOrderEventPropertiesValidatorTest {

    @Test
    void prod_Consumer_활성화_시_외부_URL이_비어있으면_시작에_실패한다() {
        prodContextRunner(
                true,
                "",
                "/api/v1/order-events"
        ).run(context -> assertStartupFailure(
                context.getStartupFailure(),
                "APP_EXTERNAL_ORDER_EVENT_BASE_URL을 설정해야 합니다."
        ));
    }

    @Test
    void prod_Consumer_활성화_시_localhost_URL이면_시작에_실패한다() {
        prodContextRunner(
                true,
                "http://localhost:8080",
                "/api/v1/order-events"
        ).run(context -> assertStartupFailure(
                context.getStartupFailure(),
                "localhost 외부 이벤트 URL을 사용할 수 없습니다."
        ));
    }

    @Test
    void prod_Consumer_활성화_시_Mock_API_경로이면_시작에_실패한다() {
        prodContextRunner(
                true,
                "https://collector.example.com",
                "/mock/v1/order-events"
        ).run(context -> assertStartupFailure(
                context.getStartupFailure(),
                "Mock API 경로를 사용할 수 없습니다."
        ));
    }

    @Test
    void prod_Consumer_활성화_시_운영_URL이면_시작한다() {
        prodContextRunner(
                true,
                "https://collector.example.com",
                "/api/v1/order-events"
        ).run(context -> assertThat(context)
                .hasNotFailed());
    }

    @Test
    void prod_Consumer_비활성화_시_외부_URL_검증을_건너뛴다() {
        prodContextRunner(
                false,
                "",
                ""
        ).run(context -> assertThat(context)
                .hasNotFailed());
    }

    private ApplicationContextRunner prodContextRunner(
            boolean consumerEnabled,
            String baseUrl,
            String path
    ) {
        return new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment()
                        .setActiveProfiles("prod"))
                .withUserConfiguration(ValidatorTestConfig.class)
                .withPropertyValues(
                        "app.kafka-order-event.topic=order.completed",
                        "app.kafka-order-event.dead-letter-topic=order.completed.DLT",
                        "app.kafka-order-event.producer.send-timeout-millis=3000",
                        "app.kafka-order-event.publisher.enabled=false",
                        "app.kafka-order-event.publisher.batch-size=10",
                        "app.kafka-order-event.publisher.fixed-delay-millis=5000",
                        "app.kafka-order-event.publisher.publish-lease-seconds=30",
                        "app.kafka-order-event.consumer.enabled=" + consumerEnabled,
                        "app.kafka-order-event.consumer.group-id=coffee-order-event-consumer",
                        "app.kafka-order-event.consumer.max-attempts=3",
                        "app.kafka-order-event.consumer.retry-interval-millis=1000",
                        "app.kafka-order-event.consumer.processing-lease-seconds=30",
                        "app.external-order-event.enabled=true",
                        "app.external-order-event.base-url=" + baseUrl,
                        "app.external-order-event.path=" + path,
                        "app.external-order-event.connect-timeout-millis=500",
                        "app.external-order-event.response-timeout-millis=1000"
                );
    }

    private void assertStartupFailure(
            Throwable startupFailure,
            String message
    ) {
        assertThat(startupFailure)
                .isNotNull()
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining(message);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({
            KafkaOrderEventProperties.class,
            ExternalOrderEventProperties.class
    })
    @Import(ProdExternalOrderEventPropertiesValidator.class)
    static class ValidatorTestConfig {
    }
}
