package com.example.coffeeorder.order.repository.projection;

import java.time.LocalDateTime;

import com.example.coffeeorder.menu.entity.MenuCategory;
import com.example.coffeeorder.menu.entity.MenuStatus;

public record PopularMenuAggregation(
        Long menuId,
        String menuName,
        MenuCategory category,
        Long currentPrice,
        MenuStatus currentStatus,
        Long orderCount,
        LocalDateTime latestOrderedAt
) {
}
