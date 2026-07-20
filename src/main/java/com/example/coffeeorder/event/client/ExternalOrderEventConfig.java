package com.example.coffeeorder.event.client;

import com.example.coffeeorder.event.service.OrderEventDeliveryProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        ExternalOrderEventProperties.class,
        OrderEventDeliveryProperties.class
})
public class ExternalOrderEventConfig {
}
