package com.example.coffeeorder.order.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.example.coffeeorder.common.exception.BusinessException;
import com.example.coffeeorder.common.exception.ErrorCode;
import com.example.coffeeorder.order.entity.Order;
import com.example.coffeeorder.order.entity.OrderItem;
import com.example.coffeeorder.order.repository.OrderItemRepository;
import com.example.coffeeorder.order.repository.OrderRepository;
import com.example.coffeeorder.payment.entity.Payment;
import com.example.coffeeorder.payment.repository.PaymentRepository;
import org.springframework.stereotype.Component;

@Component
class OrderQueryReader {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;

    OrderQueryReader(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            PaymentRepository paymentRepository
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentRepository = paymentRepository;
    }

    Order findOrderWithMember(Long orderId) {
        return orderRepository.findByIdWithMember(orderId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ORDER_NOT_FOUND
                ));
    }

    List<OrderItem> findOrderItems(Long orderId) {
        return orderItemRepository.findAllByOrder_IdOrderByIdAsc(orderId);
    }

    Payment findPayment(Long orderId) {
        return paymentRepository.findByOrder_Id(orderId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.PAYMENT_NOT_FOUND
                ));
    }

    Map<Long, List<OrderItem>> findItemsByOrderId(List<Order> orders) {
        if (orders.isEmpty()) {
            return Map.of();
        }

        List<Long> orderIds = orders.stream()
                .map(Order::getId)
                .toList();

        return orderItemRepository.findAllByOrderIds(orderIds)
                .stream()
                .collect(Collectors.groupingBy(
                        orderItem -> orderItem.getOrder()
                                .getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }
}
