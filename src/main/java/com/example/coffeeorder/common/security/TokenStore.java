package com.example.coffeeorder.common.security;

public interface TokenStore {

    void saveRefreshToken(
            Long memberId,
            String refreshToken,
            long ttlSeconds
    );

    boolean matchesRefreshToken(
            Long memberId,
            String refreshToken
    );

    void deleteRefreshToken(Long memberId);

    void blacklistAccessToken(
            String accessToken,
            long ttlSeconds
    );

    boolean isAccessTokenBlacklisted(String accessToken);
}
