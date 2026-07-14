package com.example.coffeeorder.menu.dto.request;

import jakarta.validation.constraints.Size;

public record MenuUpdateRequest(

        @Size(
                min = 1,
                max = 100,
                message = "메뉴 이름은 1자 이상 100자 이하입니다."
        )
        String name,

        @Size(
                max = 500,
                message = "메뉴 설명은 최대 500자입니다."
        )
        String description,

        String category,

        Long price
) {

    public MenuUpdateRequest {
        if (name != null) {
            name = name.trim();
        }

        if (category != null) {
            category = category.trim();
        }
    }
}
