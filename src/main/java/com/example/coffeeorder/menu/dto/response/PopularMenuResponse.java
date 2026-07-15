package com.example.coffeeorder.menu.dto.response;

import java.time.LocalDateTime;

import com.example.coffeeorder.menu.entity.MenuCategory;
import com.example.coffeeorder.menu.entity.MenuStatus;
import com.example.coffeeorder.order.repository.projection.PopularMenuAggregation;

public record PopularMenuResponse(
        Integer rank,
        Long menuId,
        String menuName,
        MenuCategory category,
        Long currentPrice,
        MenuStatus currentStatus,
        Long orderCount,
        LocalDateTime latestOrderedAt
) {

    public static PopularMenuResponse of(
            PopularMenuAggregation aggregation,
            int rank
    ) {
        return new PopularMenuResponse(
                rank,
                aggregation.menuId(),
                aggregation.menuName(),
                aggregation.category(),
                aggregation.currentPrice(),
                aggregation.currentStatus(),
                aggregation.orderCount(),
                aggregation.latestOrderedAt()
        );
    }
}
