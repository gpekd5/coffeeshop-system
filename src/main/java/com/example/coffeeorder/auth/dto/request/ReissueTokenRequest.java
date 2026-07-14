package com.example.coffeeorder.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ReissueTokenRequest(

        @NotBlank(message = "Refresh Token은 필수입니다.")
        String refreshToken
) {
}
