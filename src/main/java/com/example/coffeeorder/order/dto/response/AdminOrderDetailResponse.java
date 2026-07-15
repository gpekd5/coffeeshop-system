package com.example.coffeeorder.order.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import com.example.coffeeorder.order.entity.Order;
import com.example.coffeeorder.order.entity.OrderChannel;
import com.example.coffeeorder.order.entity.OrderItem;
import com.example.coffeeorder.order.entity.OrderStatus;
import com.example.coffeeorder.payment.entity.Payment;

public record AdminOrderDetailResponse(
        Long orderId,
        String orderNumber,
        OrderMemberResponse member,
        OrderChannel orderChannel,
        OrderStatus status,
        List<OrderItemResponse> items,
        Long totalAmount,
        OrderDetailPaymentResponse payment,
        LocalDateTime orderedAt
) {

    public static AdminOrderDetailResponse of(
            Order order,
            List<OrderItem> orderItems,
            Payment payment
    ) {
        return new AdminOrderDetailResponse(
                order.getId(),
                order.getOrderNumber(),
                OrderMemberResponse.from(order.getMember()),
                order.getOrderChannel(),
                order.getStatus(),
                orderItems.stream()
                        .map(OrderItemResponse::from)
                        .toList(),
                order.getTotalAmount(),
                OrderDetailPaymentResponse.from(payment),
                order.getOrderedAt()
        );
    }
}
