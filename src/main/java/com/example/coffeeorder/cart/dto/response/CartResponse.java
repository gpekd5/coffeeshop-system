package com.example.coffeeorder.cart.dto.response;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import com.example.coffeeorder.cart.entity.Cart;
import com.example.coffeeorder.cart.entity.CartItem;

public record CartResponse(
        Long cartId,
        List<CartItemResponse> items,
        Long expectedTotalAmount,
        LocalDateTime updatedAt
) {

    public static CartResponse of(
            Cart cart,
            List<CartItem> cartItems
    ) {
        List<CartItemResponse> items = cartItems.stream()
                .map(CartItemResponse::from)
                .toList();

        long expectedTotalAmount = items.stream()
                .mapToLong(CartItemResponse::lineAmount)
                .sum();

        return new CartResponse(
                cart.getId(),
                items,
                expectedTotalAmount,
                latestUpdatedAt(
                        cart,
                        cartItems
                )
        );
    }

    private static LocalDateTime latestUpdatedAt(
            Cart cart,
            List<CartItem> cartItems
    ) {
        return cartItems.stream()
                .map(CartItem::getUpdatedAt)
                .max(Comparator.naturalOrder())
                .orElse(cart.getUpdatedAt());
    }
}
