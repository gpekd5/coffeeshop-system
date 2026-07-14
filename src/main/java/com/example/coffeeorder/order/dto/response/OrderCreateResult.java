package com.example.coffeeorder.order.dto.response;

public record OrderCreateResult(
        OrderCreateResponse response,
        boolean alreadyProcessed
) {

    public static OrderCreateResult created(OrderCreateResponse response) {
        return new OrderCreateResult(
                response,
                false
        );
    }

    public static OrderCreateResult alreadyProcessed(OrderCreateResponse response) {
        return new OrderCreateResult(
                response,
                true
        );
    }
}
