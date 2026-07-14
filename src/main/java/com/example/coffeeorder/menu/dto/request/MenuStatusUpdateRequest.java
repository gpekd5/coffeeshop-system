package com.example.coffeeorder.menu.dto.request;

import jakarta.validation.constraints.NotBlank;

public record MenuStatusUpdateRequest(

        @NotBlank(message = "메뉴 상태는 필수입니다.")
        String status
) {

    public MenuStatusUpdateRequest {
        if (status != null) {
            status = status.trim();
        }
    }
}
