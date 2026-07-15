package com.example.coffeeorder.order.dto.response;

import java.time.LocalDateTime;

import com.example.coffeeorder.order.entity.Order;
import com.example.coffeeorder.order.entity.OrderChannel;
import com.example.coffeeorder.order.entity.OrderStatus;

public record AdminOrderSummaryResponse(
        Long orderId,
        String orderNumber,
        Long memberId,
        String memberEmail,
        OrderStatus status,
        OrderChannel orderChannel,
        Long totalAmount,
        LocalDateTime orderedAt
) {

    public static AdminOrderSummaryResponse from(Order order) {
        return new AdminOrderSummaryResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getMemberId(),
                order.getMember()
                        .getEmail(),
                order.getStatus(),
                order.getOrderChannel(),
                order.getTotalAmount(),
                order.getOrderedAt()
        );
    }
}
