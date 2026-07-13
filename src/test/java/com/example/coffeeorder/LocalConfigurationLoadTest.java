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
