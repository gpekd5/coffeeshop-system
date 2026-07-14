package com.example.coffeeorder.common.config;

import java.time.Clock;
import java.util.TimeZone;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeZoneConfig {

    private final String timeZone;

    public TimeZoneConfig(
            @Value("${app.time-zone:Asia/Seoul}") String timeZone
    ) {
        this.timeZone = timeZone;
    }

    @PostConstruct
    void setDefaultTimeZone() {
        TimeZone.setDefault(
                TimeZone.getTimeZone(timeZone)
        );
    }

    @Bean
    public Clock clock() {
        return Clock.system(
                TimeZone.getTimeZone(timeZone)
                        .toZoneId()
        );
    }
}
