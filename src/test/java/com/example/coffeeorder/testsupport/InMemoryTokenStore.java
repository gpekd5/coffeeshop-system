package com.example.coffeeorder.testsupport;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.example.coffeeorder.common.security.TokenStore;

public class InMemoryTokenStore implements TokenStore {

    private final Map<Long, String> refreshTokens =
            new ConcurrentHashMap<>();
    private final Set<String> blacklistedAccessTokens =
            ConcurrentHashMap.newKeySet();

    @Override
    public void saveRefreshToken(
            Long memberId,
            String refreshToken,
            long ttlSeconds
    ) {
        if (ttlSeconds > 0) {
            refreshTokens.put(
                    memberId,
                    refreshToken
            );
        }
    }

    @Override
    public synchronized boolean matchesRefreshToken(
            Long memberId,
            String refreshToken
    ) {
        return refreshToken.equals(refreshTokens.get(memberId));
    }

    @Override
    public synchronized boolean rotateRefreshToken(
            Long memberId,
            String currentRefreshToken,
            String newRefreshToken,
            long ttlSeconds
    ) {
        if (ttlSeconds <= 0) {
            return false;
        }

        if (!currentRefreshToken.equals(refreshTokens.get(memberId))) {
            return false;
        }

        refreshTokens.put(
                memberId,
                newRefreshToken
        );

        return true;
    }

    @Override
    public synchronized void deleteRefreshToken(Long memberId) {
        refreshTokens.remove(memberId);
    }

    @Override
    public synchronized void logoutTokens(
            Long memberId,
            String accessToken,
            long accessTokenTtlSeconds
    ) {
        if (accessTokenTtlSeconds > 0) {
            blacklistedAccessTokens.add(accessToken);
        }

        refreshTokens.remove(memberId);
    }

    @Override
    public boolean isAccessTokenBlacklisted(String accessToken) {
        return blacklistedAccessTokens.contains(accessToken);
    }

    public void clear() {
        refreshTokens.clear();
        blacklistedAccessTokens.clear();
    }
}
