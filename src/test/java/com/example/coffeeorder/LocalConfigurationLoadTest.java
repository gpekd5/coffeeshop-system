package com.example.coffeeorder;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class LocalConfigurationLoadTest {

    @Test
    void 로컬_환경_설정_파일이_로딩된다() throws IOException {
        List<PropertySource<?>> propertySources =
                new YamlPropertySourceLoader().load(
                        "application-local",
                        new ClassPathResource("application-local.yaml")
                );

        assertPropertyContains(
                propertySources,
                "spring.datasource.url",
                "jdbc:mysql://localhost:3306/coffee_order"
        );
        assertPropertyContains(
                propertySources,
                "spring.datasource.username",
                "coffee"
        );
        assertPropertyContains(
                propertySources,
                "spring.data.redis.host",
                "localhost"
        );
        assertPropertyContains(
                propertySources,
                "spring.data.redis.port",
                "6379"
        );
        assertPropertyContains(
                propertySources,
                "app.mock-order-event.enabled",
                "true"
        );
        assertPropertyContains(
                propertySources,
                "app.kafka-order-event.publisher.enabled",
                "false"
        );
        assertPropertyContains(
                propertySources,
                "app.kafka-order-event.consumer.enabled",
                "false"
        );
    }

    @Test
    void 운영_환경_설정은_외부_인프라와_JWT_시크릿을_환경변수로_받는다() throws IOException {
        List<PropertySource<?>> propertySources =
                new YamlPropertySourceLoader().load(
                        "application-prod",
                        new ClassPathResource("application-prod.yaml")
                );

        assertPropertyContains(
                propertySources,
                "spring.datasource.url",
                "SPRING_DATASOURCE_URL"
        );
        assertPropertyContains(
                propertySources,
                "spring.data.redis.host",
                "SPRING_DATA_REDIS_HOST"
        );
        assertPropertyContains(
                propertySources,
                "spring.kafka.bootstrap-servers",
                "SPRING_KAFKA_BOOTSTRAP_SERVERS"
        );
        assertPropertyContains(
                propertySources,
                "app.jwt.secret",
                "APP_JWT_SECRET"
        );
        assertPropertyContains(
                propertySources,
                "app.mock-order-event.enabled",
                "false"
        );
        assertPropertyContains(
                propertySources,
                "app.kafka-order-event.publisher.enabled",
                "false"
        );
        assertPropertyContains(
                propertySources,
                "app.kafka-order-event.consumer.enabled",
                "false"
        );
    }

    private void assertPropertyContains(
            List<PropertySource<?>> propertySources,
            String name,
            String expected
    ) {
        Object value = propertySources.stream()
                .map(propertySource -> propertySource.getProperty(name))
                .filter(property -> property != null)
                .findFirst()
                .orElse(null);

        assertNotNull(value);
        assertTrue(value.toString().contains(expected));
    }
}
