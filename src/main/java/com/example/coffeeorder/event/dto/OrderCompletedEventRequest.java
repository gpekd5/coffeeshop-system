package com.example.coffeeorder.event.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.example.coffeeorder.order.dto.response.OrderCreateResponse;
import com.example.coffeeorder.order.entity.OrderChannel;
import com.example.coffeeorder.payment.entity.PaymentMethod;

public record OrderCompletedEventRequest(
        String eventId,
        String eventType,
        Long orderId,
        String orderNumber,
        Long memberId,
        OrderChannel orderChannel,
        List<OrderCompletedEventItemRequest> items,
        Long totalAmount,
        PaymentMethod paymentMethod,
        LocalDateTime orderedAt
) {

    private static final String ORDER_COMPLETED_EVENT_TYPE = "ORDER_COMPLETED";

    public static OrderCompletedEventRequest from(
            String eventId,
            Long memberId,
            OrderCreateResponse order
    ) {
        return new OrderCompletedEventRequest(
                eventId,
                ORDER_COMPLETED_EVENT_TYPE,
                order.orderId(),
                order.orderNumber(),
                memberId,
                order.orderChannel(),
                order.items()
                        .stream()
                        .map(OrderCompletedEventItemRequest::from)
                        .toList(),
                order.totalAmount(),
                order.payment()
                        .paymentMethod(),
                order.orderedAt()
        );
    }
}
