package com.example.coffeeorder.order.dto.response;

import com.example.coffeeorder.menu.entity.MenuCategory;
import com.example.coffeeorder.order.entity.OrderItem;

public record OrderItemResponse(
        Long orderItemId,
        Long menuId,
        String menuName,
        MenuCategory menuCategory,
        Long unitPrice,
        Integer quantity,
        Long lineAmount
) {

    public static OrderItemResponse from(OrderItem orderItem) {
        return new OrderItemResponse(
                orderItem.getId(),
                orderItem.getMenuId(),
                orderItem.getMenuName(),
                orderItem.getMenuCategory(),
                orderItem.getUnitPrice(),
                orderItem.getQuantity(),
                orderItem.getLineAmount()
        );
    }
}
