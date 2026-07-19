package com.example.coffeeorder.event.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.external-order-event")
public record ExternalOrderEventProperties(
        boolean enabled,
        String baseUrl,
        String path,
        long connectTimeoutMillis,
        long responseTimeoutMillis
) {
}
