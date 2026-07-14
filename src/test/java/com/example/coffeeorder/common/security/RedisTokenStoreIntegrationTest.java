package com.example.coffeeorder.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisTokenStoreIntegrationTest {

    private static final String REDIS_HOST =
            System.getProperty(
                    "test.redis.host",
                    "localhost"
            );
    private static final int REDIS_PORT =
            Integer.getInteger(
                    "test.redis.port",
                    6379
            );

    @Test
    void 실제_Redis에_RefreshToken과_AccessToken_Blacklist를_TTL과_함께_저장한다() {
        assumeTrue(
                isRedisAvailable(),
                "Redis is not available on localhost:6379"
        );

        LettuceConnectionFactory connectionFactory =
                new LettuceConnectionFactory(
                        new RedisStandaloneConfiguration(
                                REDIS_HOST,
                                REDIS_PORT
                        )
                );
        connectionFactory.afterPropertiesSet();

        try {
            StringRedisTemplate redisTemplate =
                    new StringRedisTemplate(connectionFactory);
            redisTemplate.afterPropertiesSet();
            RedisTokenStore redisTokenStore =
                    new RedisTokenStore(redisTemplate);
            Long memberId = Math.abs(UUID.randomUUID()
                    .getMostSignificantBits());
            String refreshToken = "refresh-token-" + UUID.randomUUID();
            String accessToken = "access-token-" + UUID.randomUUID();

            try {
                redisTokenStore.saveRefreshToken(
                        memberId,
                        refreshToken,
                        5L
                );
                redisTokenStore.blacklistAccessToken(
                        accessToken,
                        5L
                );

                assertThat(redisTokenStore.matchesRefreshToken(
                        memberId,
                        refreshToken
                )).isTrue();
                assertThat(redisTokenStore.isAccessTokenBlacklisted(
                        accessToken
                )).isTrue();
                assertThat(redisTemplate.getExpire(refreshKey(memberId)))
                        .isPositive()
                        .isLessThanOrEqualTo(5L);

                redisTokenStore.deleteRefreshToken(memberId);

                assertThat(redisTokenStore.matchesRefreshToken(
                        memberId,
                        refreshToken
                )).isFalse();
            } finally {
                redisTemplate.delete(refreshKey(memberId));
                Set<String> blacklistKeys =
                        redisTemplate.keys("auth:blacklist:access:*");

                if (blacklistKeys != null && !blacklistKeys.isEmpty()) {
                    redisTemplate.delete(blacklistKeys);
                }
            }
        } finally {
            connectionFactory.destroy();
        }
    }

    private boolean isRedisAvailable() {
        try (Socket socket = new Socket()) {
            socket.connect(
                    new InetSocketAddress(
                            REDIS_HOST,
                            REDIS_PORT
                    ),
                    300
            );

            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private String refreshKey(Long memberId) {
        return "auth:refresh:" + memberId;
    }
}
