package com.example.coffeeorder.cart.dto.request;

import jakarta.validation.constraints.NotNull;

public record UpdateCartItemQuantityRequest(

        @NotNull(message = "수량은 필수입니다.")
        Integer quantity
) {
}
