package com.example.coffeeorder.event.outbox.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.example.coffeeorder.order.dto.response.OrderCreateResponse;
import com.example.coffeeorder.order.dto.response.OrderItemResponse;
import com.example.coffeeorder.order.entity.OrderChannel;
import com.example.coffeeorder.payment.entity.PaymentMethod;

public record OrderCompletedOutboxPayload(
        String eventId,
        String eventType,
        String aggregateType,
        Long aggregateId,
        Long orderId,
        String orderNumber,
        Long memberId,
        OrderChannel orderChannel,
        List<OrderItemResponse> items,
        Long totalAmount,
        PaymentMethod paymentMethod,
        LocalDateTime orderedAt,
        LocalDateTime occurredAt
) {

    private static final String ORDER_COMPLETED_EVENT_TYPE = "ORDER_COMPLETED";
    private static final String ORDER_AGGREGATE_TYPE = "ORDER";

    public static OrderCompletedOutboxPayload from(
            String eventId,
            Long memberId,
            OrderCreateResponse order,
            LocalDateTime occurredAt
    ) {
        return new OrderCompletedOutboxPayload(
                eventId,
                ORDER_COMPLETED_EVENT_TYPE,
                ORDER_AGGREGATE_TYPE,
                order.orderId(),
                order.orderId(),
                order.orderNumber(),
                memberId,
                order.orderChannel(),
                order.items(),
                order.totalAmount(),
                order.payment()
                        .paymentMethod(),
                order.orderedAt(),
                occurredAt
        );
    }
}
