package com.example.coffeeorder.order.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import com.example.coffeeorder.order.entity.Order;
import com.example.coffeeorder.order.entity.OrderChannel;
import com.example.coffeeorder.order.entity.OrderItem;
import com.example.coffeeorder.order.entity.OrderStatus;
import com.example.coffeeorder.payment.entity.Payment;

public record OrderCreateResponse(
        Long orderId,
        String orderNumber,
        OrderChannel orderChannel,
        OrderStatus status,
        List<OrderItemResponse> items,
        Long totalAmount,
        OrderPaymentResponse payment,
        OrderPointResponse point,
        LocalDateTime orderedAt
) {

    public static OrderCreateResponse of(
            Order order,
            List<OrderItem> orderItems,
            Payment payment,
            long usedAmount,
            long balanceAfter
    ) {
        return new OrderCreateResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getOrderChannel(),
                order.getStatus(),
                orderItems.stream()
                        .map(OrderItemResponse::from)
                        .toList(),
                order.getTotalAmount(),
                OrderPaymentResponse.from(payment),
                new OrderPointResponse(
                        usedAmount,
                        balanceAfter
                ),
                order.getOrderedAt()
        );
    }
}
