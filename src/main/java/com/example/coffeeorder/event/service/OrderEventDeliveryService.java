package com.example.coffeeorder.event.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

import com.example.coffeeorder.event.client.ExternalOrderEventClient;
import com.example.coffeeorder.event.client.ExternalOrderEventSendResult;
import com.example.coffeeorder.event.dto.OrderCompletedEventRequest;
import com.example.coffeeorder.order.dto.response.OrderCreateResponse;
import com.example.coffeeorder.order.entity.OrderStatus;
import com.example.coffeeorder.payment.entity.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OrderEventDeliveryService {

    private static final Logger log =
            LoggerFactory.getLogger(OrderEventDeliveryService.class);

    private final ExternalOrderEventClient externalOrderEventClient;
    private final ExternalOrderEventLogService externalOrderEventLogService;
    private final Clock clock;
    private final boolean enabled;

    public OrderEventDeliveryService(
            ExternalOrderEventClient externalOrderEventClient,
            ExternalOrderEventLogService externalOrderEventLogService,
            Clock clock,
            @Value("${app.external-order-event.enabled:true}") boolean enabled
    ) {
        this.externalOrderEventClient = externalOrderEventClient;
        this.externalOrderEventLogService = externalOrderEventLogService;
        this.clock = clock;
        this.enabled = enabled;
    }

    public void sendOrderCompletedEvent(
            Long memberId,
            OrderCreateResponse response
    ) {
        if (!enabled || !isCompletedPointPayment(response)) {
            return;
        }

        OrderCompletedEventRequest event = OrderCompletedEventRequest.from(
                UUID.randomUUID()
                        .toString(),
                memberId,
                response
        );
        ExternalOrderEventSendResult result = send(event);

        try {
            externalOrderEventLogService.record(
                    event,
                    result
            );
        } catch (RuntimeException exception) {
            log.error(
                    "외부 주문 이벤트 전송 결과 기록에 실패했습니다. eventId={}, orderId={}",
                    event.eventId(),
                    event.orderId(),
                    exception
            );
        }
    }

    private ExternalOrderEventSendResult send(OrderCompletedEventRequest event) {
        try {
            return externalOrderEventClient.send(event);
        } catch (RuntimeException exception) {
            log.error(
                    "외부 주문 이벤트 전송 중 예기치 않은 예외가 발생했습니다. eventId={}, orderId={}",
                    event.eventId(),
                    event.orderId(),
                    exception
            );

            return ExternalOrderEventSendResult.failed(
                    null,
                    exception.getMessage(),
                    LocalDateTime.now(clock)
            );
        }
    }

    private boolean isCompletedPointPayment(OrderCreateResponse response) {
        return response.status() == OrderStatus.COMPLETED
                && response.payment() != null
                && response.payment()
                        .paymentStatus() == PaymentStatus.COMPLETED;
    }
}
