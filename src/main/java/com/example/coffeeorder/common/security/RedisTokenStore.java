package com.example.coffeeorder.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;

import com.example.coffeeorder.common.exception.ErrorCode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisTokenStore implements TokenStore {

    private static final String REFRESH_TOKEN_KEY_PREFIX = "auth:refresh:";
    private static final String ACCESS_TOKEN_BLACKLIST_KEY_PREFIX =
            "auth:blacklist:access:";
    private static final String BLACKLISTED_VALUE = "blacklisted";

    private final StringRedisTemplate redisTemplate;

    public RedisTokenStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void saveRefreshToken(
            Long memberId,
            String refreshToken,
            long ttlSeconds
    ) {
        if (ttlSeconds <= 0) {
            return;
        }

        redisTemplate.opsForValue()
                .set(
                        refreshTokenKey(memberId),
                        hash(refreshToken),
                        Duration.ofSeconds(ttlSeconds)
                );
    }

    @Override
    public boolean matchesRefreshToken(
            Long memberId,
            String refreshToken
    ) {
        String savedTokenHash = redisTemplate.opsForValue()
                .get(refreshTokenKey(memberId));

        return hash(refreshToken).equals(savedTokenHash);
    }

    @Override
    public void deleteRefreshToken(Long memberId) {
        redisTemplate.delete(refreshTokenKey(memberId));
    }

    @Override
    public void blacklistAccessToken(
            String accessToken,
            long ttlSeconds
    ) {
        if (ttlSeconds <= 0) {
            return;
        }

        redisTemplate.opsForValue()
                .set(
                        accessTokenBlacklistKey(accessToken),
                        BLACKLISTED_VALUE,
                        Duration.ofSeconds(ttlSeconds)
                );
    }

    @Override
    public boolean isAccessTokenBlacklisted(String accessToken) {
        return Boolean.TRUE.equals(
                redisTemplate.hasKey(accessTokenBlacklistKey(accessToken))
        );
    }

    private String refreshTokenKey(Long memberId) {
        return REFRESH_TOKEN_KEY_PREFIX + memberId;
    }

    private String accessTokenBlacklistKey(String accessToken) {
        return ACCESS_TOKEN_BLACKLIST_KEY_PREFIX + hash(accessToken);
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(digest.digest(
                            token.getBytes(StandardCharsets.UTF_8)
                    ));
        } catch (Exception exception) {
            throw new JwtAuthenticationException(
                    ErrorCode.INVALID_TOKEN
            );
        }
    }
}
