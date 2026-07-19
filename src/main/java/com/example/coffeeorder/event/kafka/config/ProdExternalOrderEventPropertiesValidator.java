package com.example.coffeeorder.event.kafka.config;

import java.net.URI;

import com.example.coffeeorder.event.client.ExternalOrderEventProperties;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("prod")
public class ProdExternalOrderEventPropertiesValidator implements InitializingBean {

    private static final String MOCK_ORDER_EVENT_PATH = "/mock/v1/order-events";
    private static final String HTTP = "http";
    private static final String HTTPS = "https";

    private final KafkaOrderEventProperties kafkaOrderEventProperties;
    private final ExternalOrderEventProperties externalOrderEventProperties;

    public ProdExternalOrderEventPropertiesValidator(
            KafkaOrderEventProperties kafkaOrderEventProperties,
            ExternalOrderEventProperties externalOrderEventProperties
    ) {
        this.kafkaOrderEventProperties = kafkaOrderEventProperties;
        this.externalOrderEventProperties = externalOrderEventProperties;
    }

    @Override
    public void afterPropertiesSet() {
        if (!kafkaOrderEventProperties.consumer()
                .enabled()) {
            return;
        }

        validateBaseUrl();
        validatePath();
    }

    private void validateBaseUrl() {
        String baseUrl = externalOrderEventProperties.baseUrl();

        if (!StringUtils.hasText(baseUrl)) {
            throw invalidConfiguration(
                    "APP_EXTERNAL_ORDER_EVENT_BASE_URL을 설정해야 합니다."
            );
        }

        URI uri = createUri(baseUrl);

        if (!HTTP.equalsIgnoreCase(uri.getScheme())
                && !HTTPS.equalsIgnoreCase(uri.getScheme())) {
            throw invalidConfiguration(
                    "APP_EXTERNAL_ORDER_EVENT_BASE_URL은 http 또는 https URL이어야 합니다."
            );
        }

        String host = uri.getHost();

        if (!StringUtils.hasText(host) || isLocalHost(host)) {
            throw invalidConfiguration(
                    "운영 Kafka Consumer는 localhost 외부 이벤트 URL을 사용할 수 없습니다."
            );
        }
    }

    private URI createUri(String baseUrl) {
        try {
            return URI.create(baseUrl);
        } catch (IllegalArgumentException exception) {
            throw invalidConfiguration(
                    "APP_EXTERNAL_ORDER_EVENT_BASE_URL 형식이 올바르지 않습니다.",
                    exception
            );
        }
    }

    private boolean isLocalHost(String host) {
        String normalizedHost = host.toLowerCase();

        return normalizedHost.equals("localhost")
                || normalizedHost.equals("127.0.0.1")
                || normalizedHost.equals("::1")
                || normalizedHost.equals("0:0:0:0:0:0:0:1");
    }

    private void validatePath() {
        String path = externalOrderEventProperties.path();

        if (!StringUtils.hasText(path)) {
            throw invalidConfiguration(
                    "APP_EXTERNAL_ORDER_EVENT_PATH를 설정해야 합니다."
            );
        }

        String normalizedPath = path.startsWith("/") ? path : "/" + path;

        if (MOCK_ORDER_EVENT_PATH.equals(normalizedPath)) {
            throw invalidConfiguration(
                    "운영 Kafka Consumer는 Mock API 경로를 사용할 수 없습니다."
            );
        }
    }

    private IllegalStateException invalidConfiguration(String message) {
        return new IllegalStateException(
                "운영 외부 주문 이벤트 설정이 올바르지 않습니다. " + message
        );
    }

    private IllegalStateException invalidConfiguration(
            String message,
            Throwable cause
    ) {
        return new IllegalStateException(
                "운영 외부 주문 이벤트 설정이 올바르지 않습니다. " + message,
                cause
        );
    }
}
