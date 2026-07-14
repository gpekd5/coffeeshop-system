package com.example.coffeeorder.cart.dto.request;

import jakarta.validation.constraints.NotNull;

public record AddCartItemRequest(

        @NotNull(message = "메뉴 식별자는 필수입니다.")
        Long menuId,

        @NotNull(message = "수량은 필수입니다.")
        Integer quantity
) {
}
