package com.example.coffeeorder.event.client;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ExternalOrderEventProperties.class)
public class ExternalOrderEventConfig {
}
