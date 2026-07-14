package com.example.coffeeorder.point.dto.request;

import jakarta.validation.constraints.NotNull;

public record PointChargeRequest(
        @NotNull(message = "충전 금액은 필수입니다.")
        Long amount
) {
}
