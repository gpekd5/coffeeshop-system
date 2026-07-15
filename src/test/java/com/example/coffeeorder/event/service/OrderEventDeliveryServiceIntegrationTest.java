package com.example.coffeeorder.event.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.example.coffeeorder.event.entity.ExternalOrderEventLog;
import com.example.coffeeorder.event.entity.ExternalOrderEventStatus;
import com.example.coffeeorder.event.repository.ExternalOrderEventLogRepository;
import com.example.coffeeorder.menu.entity.MenuCategory;
import com.example.coffeeorder.order.dto.response.OrderCreateResponse;
import com.example.coffeeorder.order.dto.response.OrderItemResponse;
import com.example.coffeeorder.order.dto.response.OrderPaymentResponse;
import com.example.coffeeorder.order.dto.response.OrderPointResponse;
import com.example.coffeeorder.order.entity.OrderChannel;
import com.example.coffeeorder.order.entity.OrderStatus;
import com.example.coffeeorder.payment.entity.PaymentMethod;
import com.example.coffeeorder.payment.entity.PaymentStatus;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class OrderEventDeliveryServiceIntegrationTest {

    private static final StubExternalServer STUB_SERVER =
            StubExternalServer.start();

    @Autowired
    private OrderEventDeliveryService orderEventDeliveryService;

    @Autowired
    private ExternalOrderEventLogRepository externalOrderEventLogRepository;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "app.external-order-event.enabled",
                () -> "true"
        );
        registry.add(
                "app.external-order-event.base-url",
                STUB_SERVER::baseUrl
        );
        registry.add(
                "app.external-order-event.path",
                () -> "/order-events"
        );
        registry.add(
                "app.external-order-event.response-timeout-millis",
                () -> "500"
        );
    }

    @BeforeEach
    void setUp() {
        externalOrderEventLogRepository.deleteAll();
        STUB_SERVER.reset();
    }

    @AfterAll
    static void tearDown() {
        STUB_SERVER.stop();
    }

    @Test
    void 외부_API_정상_응답은_SUCCESS_상태로_기록된다() {
        STUB_SERVER.respondWith(
                200,
                0
        );

        orderEventDeliveryService.sendOrderCompletedEvent(
                1L,
                주문_응답을_생성한다()
        );

        ExternalOrderEventLog log = 저장된_로그를_조회한다();

        assertThat(STUB_SERVER.requestCount()).isEqualTo(1);
        assertThat(STUB_SERVER.lastRequestBody()).contains(
                "\"eventType\":\"ORDER_COMPLETED\"",
                "\"orderId\":50",
                "\"memberId\":1",
                "\"menuId\":1"
        );
        assertThat(log.getStatus()).isEqualTo(ExternalOrderEventStatus.SUCCESS);
        assertThat(log.getResponseStatusCode()).isEqualTo(200);
        assertThat(log.getErrorCode()).isNull();
        assertThat(log.getAttemptCount()).isEqualTo(1);
    }

    @Test
    void 외부_API_500_응답은_FAILED_상태와_실패_코드로_기록된다() {
        STUB_SERVER.respondWith(
                500,
                0
        );

        orderEventDeliveryService.sendOrderCompletedEvent(
                1L,
                주문_응답을_생성한다()
        );

        ExternalOrderEventLog log = 저장된_로그를_조회한다();

        assertThat(STUB_SERVER.requestCount()).isEqualTo(1);
        assertThat(log.getStatus()).isEqualTo(ExternalOrderEventStatus.FAILED);
        assertThat(log.getResponseStatusCode()).isEqualTo(500);
        assertThat(log.getErrorCode()).isEqualTo("EXTERNAL_DATA_COLLECTION_FAILED");
        assertThat(log.getErrorMessage()).contains("500");
    }

    @Test
    void 외부_API_타임아웃은_TIMEOUT_상태와_타임아웃_코드로_기록된다() {
        STUB_SERVER.respondWith(
                200,
                1_200
        );

        orderEventDeliveryService.sendOrderCompletedEvent(
                1L,
                주문_응답을_생성한다()
        );

        ExternalOrderEventLog log = 저장된_로그를_조회한다();

        assertThat(STUB_SERVER.requestCount()).isEqualTo(1);
        assertThat(log.getStatus()).isEqualTo(ExternalOrderEventStatus.TIMEOUT);
        assertThat(log.getResponseStatusCode()).isNull();
        assertThat(log.getErrorCode()).isEqualTo("EXTERNAL_DATA_COLLECTION_TIMEOUT");
    }

    private ExternalOrderEventLog 저장된_로그를_조회한다() {
        return externalOrderEventLogRepository.findAll()
                .getFirst();
    }

    private OrderCreateResponse 주문_응답을_생성한다() {
        return new OrderCreateResponse(
                50L,
                "ORD-20260713-000050",
                OrderChannel.WEB_CART,
                OrderStatus.COMPLETED,
                List.of(new OrderItemResponse(
                        70L,
                        1L,
                        "아메리카노",
                        MenuCategory.COFFEE,
                        4_500L,
                        2,
                        9_000L
                )),
                9_000L,
                new OrderPaymentResponse(
                        80L,
                        PaymentMethod.POINT,
                        PaymentStatus.COMPLETED,
                        9_000L,
                        LocalDateTime.of(
                                2026,
                                7,
                                13,
                                14,
                                0
                        )
                ),
                new OrderPointResponse(
                        9_000L,
                        1_000L
                ),
                LocalDateTime.of(
                        2026,
                        7,
                        13,
                        14,
                        0
                )
        );
    }

    private static class StubExternalServer {

        private final HttpServer server;
        private final ExecutorService executorService;
        private final AtomicInteger status = new AtomicInteger(200);
        private final AtomicInteger delayMillis = new AtomicInteger(0);
        private final AtomicInteger requestCount = new AtomicInteger(0);
        private final AtomicReference<String> lastRequestBody =
                new AtomicReference<>("");

        private StubExternalServer(HttpServer server) {
            this.server = server;
            this.executorService = Executors.newCachedThreadPool();
            this.server.createContext(
                    "/order-events",
                    this::handle
            );
            this.server.setExecutor(executorService);
            this.server.start();
        }

        static StubExternalServer start() {
            try {
                return new StubExternalServer(HttpServer.create(
                        new InetSocketAddress(0),
                        0
                ));
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }

        String baseUrl() {
            return "http://localhost:" + server.getAddress()
                    .getPort();
        }

        void respondWith(
                int statusCode,
                int delayMillis
        ) {
            this.status.set(statusCode);
            this.delayMillis.set(delayMillis);
        }

        void reset() {
            respondWith(
                    200,
                    0
            );
            requestCount.set(0);
            lastRequestBody.set("");
        }

        int requestCount() {
            return requestCount.get();
        }

        String lastRequestBody() {
            return lastRequestBody.get();
        }

        void stop() {
            server.stop(0);
            executorService.shutdownNow();
        }

        private void handle(HttpExchange exchange) throws IOException {
            requestCount.incrementAndGet();
            lastRequestBody.set(new String(
                    exchange.getRequestBody()
                            .readAllBytes(),
                    StandardCharsets.UTF_8
            ));
            sleep();

            byte[] response = "{\"success\":true,\"message\":\"ok\"}"
                    .getBytes(StandardCharsets.UTF_8);

            exchange.sendResponseHeaders(
                    status.get(),
                    response.length
            );
            exchange.getResponseBody()
                    .write(response);
            exchange.close();
        }

        private void sleep() {
            try {
                Thread.sleep(delayMillis.get());
            } catch (InterruptedException exception) {
                Thread.currentThread()
                        .interrupt();
            }
        }
    }
}
