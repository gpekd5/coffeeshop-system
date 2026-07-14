package com.example.coffeeorder.menu.dto.response;

import java.time.LocalDateTime;

import com.example.coffeeorder.menu.entity.Menu;
import com.example.coffeeorder.menu.entity.MenuCategory;
import com.example.coffeeorder.menu.entity.MenuStatus;

public record MenuResponse(
        Long menuId,
        String name,
        String description,
        MenuCategory category,
        Long price,
        MenuStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static MenuResponse from(Menu menu) {
        return new MenuResponse(
                menu.getId(),
                menu.getName(),
                menu.getDescription(),
                menu.getCategory(),
                menu.getPrice(),
                menu.getStatus(),
                menu.getCreatedAt(),
                menu.getUpdatedAt()
        );
    }
}
