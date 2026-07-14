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

    boolean rotateRefreshToken(
            Long memberId,
            String currentRefreshToken,
            String newRefreshToken,
            long ttlSeconds
    );

    void deleteRefreshToken(Long memberId);

    void logoutTokens(
            Long memberId,
            String accessToken,
            long accessTokenTtlSeconds
    );

    boolean isAccessTokenBlacklisted(String accessToken);
}
