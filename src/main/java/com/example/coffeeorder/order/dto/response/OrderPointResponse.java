package com.example.coffeeorder.order.dto.response;

public record OrderPointResponse(
        Long usedAmount,
        Long balanceAfter
) {
}
