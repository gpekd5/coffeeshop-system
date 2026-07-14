package com.example.coffeeorder.cart.dto.response;

import com.example.coffeeorder.cart.entity.CartItem;
import com.example.coffeeorder.menu.entity.Menu;
import com.example.coffeeorder.menu.entity.MenuCategory;
import com.example.coffeeorder.menu.entity.MenuStatus;

public record CartItemResponse(
        Long cartItemId,
        Long menuId,
        String menuName,
        MenuCategory category,
        Long unitPrice,
        Integer quantity,
        Long lineAmount,
        MenuStatus menuStatus
) {

    public static CartItemResponse from(CartItem cartItem) {
        Menu menu = cartItem.getMenu();

        return new CartItemResponse(
                cartItem.getId(),
                menu.getId(),
                menu.getName(),
                menu.getCategory(),
                menu.getPrice(),
                cartItem.getQuantity(),
                cartItem.calculateLineAmount(),
                menu.getStatus()
        );
    }
}
