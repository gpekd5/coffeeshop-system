package com.example.coffeeorder.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import com.example.coffeeorder.common.exception.ErrorCode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisTokenStore implements TokenStore {

    private static final String REFRESH_TOKEN_KEY_PREFIX = "auth:refresh:";
    private static final String ACCESS_TOKEN_BLACKLIST_KEY_PREFIX =
            "auth:blacklist:access:";
    private static final String BLACKLISTED_VALUE = "blacklisted";
    private static final RedisScript<Long> ROTATE_REFRESH_TOKEN_SCRIPT =
            RedisScript.of(
                    """
                    local current = redis.call('GET', KEYS[1])
                    if current ~= ARGV[1] then
                        return 0
                    end
                    redis.call('SET', KEYS[1], ARGV[2], 'EX', tonumber(ARGV[3]))
                    return 1
                    """,
                    Long.class
            );
    private static final RedisScript<Long> LOGOUT_TOKENS_SCRIPT =
            RedisScript.of(
                    """
                    if tonumber(ARGV[2]) > 0 then
                        redis.call('SET', KEYS[2], ARGV[1], 'EX', tonumber(ARGV[2]))
                    end
                    redis.call('DEL', KEYS[1])
                    return 1
                    """,
                    Long.class
            );

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
    public boolean rotateRefreshToken(
            Long memberId,
            String currentRefreshToken,
            String newRefreshToken,
            long ttlSeconds
    ) {
        if (ttlSeconds <= 0) {
            return false;
        }

        Long result = redisTemplate.execute(
                ROTATE_REFRESH_TOKEN_SCRIPT,
                List.of(refreshTokenKey(memberId)),
                hash(currentRefreshToken),
                hash(newRefreshToken),
                String.valueOf(ttlSeconds)
        );

        return Long.valueOf(1L)
                .equals(result);
    }

    @Override
    public void deleteRefreshToken(Long memberId) {
        redisTemplate.delete(refreshTokenKey(memberId));
    }

    @Override
    public void logoutTokens(
            Long memberId,
            String accessToken,
            long accessTokenTtlSeconds
    ) {
        redisTemplate.execute(
                LOGOUT_TOKENS_SCRIPT,
                List.of(
                        refreshTokenKey(memberId),
                        accessTokenBlacklistKey(accessToken)
                ),
                BLACKLISTED_VALUE,
                String.valueOf(accessTokenTtlSeconds)
        );
    }

    @Override
    public boolean isAccessTokenBlacklisted(String accessToken) {
        return Boolean.TRUE.equals(
                redisTemplate.hasKey(accessTokenBlacklistKey(accessToken))
        );
    }

    String refreshTokenKey(Long memberId) {
        return REFRESH_TOKEN_KEY_PREFIX + memberId;
    }

    String accessTokenBlacklistKey(String accessToken) {
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
