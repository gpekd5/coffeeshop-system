package com.example.coffeeorder.cart.dto.response;

import com.example.coffeeorder.cart.entity.CartItem;
import com.example.coffeeorder.menu.entity.Menu;

public record CartItemMutationResponse(
        Long cartItemId,
        Long menuId,
        String menuName,
        Long unitPrice,
        Integer quantity,
        Long lineAmount
) {

    public static CartItemMutationResponse from(CartItem cartItem) {
        Menu menu = cartItem.getMenu();

        return new CartItemMutationResponse(
                cartItem.getId(),
                menu.getId(),
                menu.getName(),
                menu.getPrice(),
                cartItem.getQuantity(),
                cartItem.calculateLineAmount()
        );
    }
}
