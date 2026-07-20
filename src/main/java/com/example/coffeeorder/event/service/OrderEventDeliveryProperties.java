package com.example.coffeeorder.event.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.order-event-delivery")
public record OrderEventDeliveryProperties(
        OrderEventDeliveryMode mode
) {

    private static final OrderEventDeliveryMode DEFAULT_MODE =
            OrderEventDeliveryMode.OUTBOX;

    @Override
    public OrderEventDeliveryMode mode() {
        if (mode == null) {
            return DEFAULT_MODE;
        }

        return mode;
    }
}
