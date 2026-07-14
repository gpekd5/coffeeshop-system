package com.example.coffeeorder.order.facade;

import com.example.coffeeorder.order.dto.response.OrderCreateResponse;
import com.example.coffeeorder.order.service.OrderTransactionService;
import org.springframework.stereotype.Component;

@Component
public class OrderFacade {

    private final OrderTransactionService orderTransactionService;

    public OrderFacade(OrderTransactionService orderTransactionService) {
        this.orderTransactionService = orderTransactionService;
    }

    public OrderCreateResponse createOrder(Long memberId) {
        return orderTransactionService.createOrder(memberId);
    }
}
