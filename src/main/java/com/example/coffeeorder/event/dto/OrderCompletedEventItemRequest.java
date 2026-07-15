package com.example.coffeeorder.event.dto;

import com.example.coffeeorder.menu.entity.MenuCategory;
import com.example.coffeeorder.order.dto.response.OrderItemResponse;

public record OrderCompletedEventItemRequest(
        Long menuId,
        String menuName,
        MenuCategory menuCategory,
        Long unitPrice,
        Integer quantity,
        Long lineAmount
) {

    public static OrderCompletedEventItemRequest from(OrderItemResponse item) {
        return new OrderCompletedEventItemRequest(
                item.menuId(),
                item.menuName(),
                item.menuCategory(),
                item.unitPrice(),
                item.quantity(),
                item.lineAmount()
        );
    }
}
