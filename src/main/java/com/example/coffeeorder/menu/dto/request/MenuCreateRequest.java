package com.example.coffeeorder.menu.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MenuCreateRequest(

        @NotBlank(message = "메뉴 이름은 필수입니다.")
        @Size(
                max = 100,
                message = "메뉴 이름은 최대 100자입니다."
        )
        String name,

        @Size(
                max = 500,
                message = "메뉴 설명은 최대 500자입니다."
        )
        String description,

        @NotBlank(message = "메뉴 카테고리는 필수입니다.")
        String category,

        @NotNull(message = "메뉴 가격은 필수입니다.")
        Long price,

        String status
) {

    public MenuCreateRequest {
        if (category != null) {
            category = category.trim();
        }

        if (status != null) {
            status = status.trim();
        }
    }
}
