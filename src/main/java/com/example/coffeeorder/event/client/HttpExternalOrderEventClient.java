package com.example.coffeeorder.event.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

import com.example.coffeeorder.event.dto.OrderCompletedEventRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class HttpExternalOrderEventClient implements ExternalOrderEventClient {

    private static final String CONTENT_TYPE = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final URI endpointUri;
    private final Duration responseTimeout;

    public HttpExternalOrderEventClient(
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${app.external-order-event.base-url}") String baseUrl,
            @Value("${app.external-order-event.path}") String path,
            @Value("${app.external-order-event.connect-timeout-millis}") long connectTimeoutMillis,
            @Value("${app.external-order-event.response-timeout-millis}") long responseTimeoutMillis
    ) {
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.endpointUri = createEndpointUri(
                baseUrl,
                path
        );
        this.responseTimeout = Duration.ofMillis(responseTimeoutMillis);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMillis))
                .build();
    }

    @Override
    public ExternalOrderEventSendResult send(OrderCompletedEventRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(
                    createRequest(request),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );

            if (isSuccessful(response.statusCode())) {
                return ExternalOrderEventSendResult.success(
                        response.statusCode(),
                        now()
                );
            }

            return ExternalOrderEventSendResult.failed(
                    response.statusCode(),
                    "외부 데이터 수집 API가 " + response.statusCode() + " 상태를 반환했습니다.",
                    now()
            );
        } catch (HttpTimeoutException exception) {
            return ExternalOrderEventSendResult.timeout(
                    exception.getMessage(),
                    now()
            );
        } catch (InterruptedException exception) {
            Thread.currentThread()
                    .interrupt();

            return ExternalOrderEventSendResult.timeout(
                    "외부 데이터 수집 API 호출이 인터럽트되었습니다.",
                    now()
            );
        } catch (IOException | RuntimeException exception) {
            return ExternalOrderEventSendResult.failed(
                    null,
                    exception.getMessage(),
                    now()
            );
        }
    }

    private HttpRequest createRequest(OrderCompletedEventRequest request)
            throws IOException {
        return HttpRequest.newBuilder(endpointUri)
                .timeout(responseTimeout)
                .header(
                        CONTENT_TYPE,
                        APPLICATION_JSON
                )
                .POST(HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(request),
                        StandardCharsets.UTF_8
                ))
                .build();
    }

    private URI createEndpointUri(
            String baseUrl,
            String path
    ) {
        String normalizedBaseUrl = baseUrl.endsWith("/")
                ? baseUrl.substring(
                        0,
                        baseUrl.length() - 1
                )
                : baseUrl;
        String normalizedPath = path.startsWith("/") ? path : "/" + path;

        return URI.create(normalizedBaseUrl + normalizedPath);
    }

    private boolean isSuccessful(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
