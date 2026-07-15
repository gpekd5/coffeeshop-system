package com.example.coffeeorder.order.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import com.example.coffeeorder.order.entity.Order;
import com.example.coffeeorder.order.entity.OrderChannel;
import com.example.coffeeorder.order.entity.OrderItem;
import com.example.coffeeorder.order.entity.OrderStatus;

public record OrderSummaryResponse(
        Long orderId,
        String orderNumber,
        OrderStatus status,
        OrderChannel orderChannel,
        String representativeMenuName,
        Integer additionalItemCount,
        Long totalAmount,
        LocalDateTime orderedAt
) {

    public static OrderSummaryResponse of(
            Order order,
            List<OrderItem> orderItems
    ) {
        String representativeMenuName = orderItems.isEmpty()
                ? null
                : orderItems.getFirst()
                        .getMenuName();

        return new OrderSummaryResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getOrderChannel(),
                representativeMenuName,
                Math.max(
                        orderItems.size() - 1,
                        0
                ),
                order.getTotalAmount(),
                order.getOrderedAt()
        );
    }
}
