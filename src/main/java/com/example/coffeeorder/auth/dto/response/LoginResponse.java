package com.example.coffeeorder.auth.dto.response;

import com.example.coffeeorder.common.security.TokenResponse;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long accessTokenExpiresIn,
        long refreshTokenExpiresIn
) {

    public static LoginResponse from(TokenResponse tokenResponse) {
        return new LoginResponse(
                tokenResponse.accessToken(),
                tokenResponse.refreshToken(),
                tokenResponse.tokenType(),
                tokenResponse.accessTokenExpiresIn(),
                tokenResponse.refreshTokenExpiresIn()
        );
    }
}
