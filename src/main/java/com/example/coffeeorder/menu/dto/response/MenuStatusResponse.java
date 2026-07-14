package com.example.coffeeorder.menu.dto.response;

import java.time.LocalDateTime;

import com.example.coffeeorder.menu.entity.Menu;
import com.example.coffeeorder.menu.entity.MenuStatus;

public record MenuStatusResponse(
        Long menuId,
        MenuStatus status,
        LocalDateTime updatedAt
) {

    public static MenuStatusResponse from(Menu menu) {
        return new MenuStatusResponse(
                menu.getId(),
                menu.getStatus(),
                menu.getUpdatedAt()
        );
    }
}
