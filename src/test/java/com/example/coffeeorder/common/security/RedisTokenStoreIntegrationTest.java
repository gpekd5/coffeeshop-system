package com.example.coffeeorder.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
        try (RedisFixture fixture = redisFixture()) {
            StringRedisTemplate redisTemplate = fixture.redisTemplate();
            RedisTokenStore redisTokenStore = fixture.redisTokenStore();
            Long memberId = Math.abs(UUID.randomUUID()
                    .getMostSignificantBits());
            String refreshToken = "refresh-token-" + UUID.randomUUID();
            String accessToken = "access-token-" + UUID.randomUUID();
            String blacklistKey =
                    redisTokenStore.accessTokenBlacklistKey(accessToken);
            String otherBlacklistKey =
                    "auth:blacklist:access:other-" + UUID.randomUUID();

            try {
                redisTokenStore.saveRefreshToken(
                        memberId,
                        refreshToken,
                        5L
                );
                redisTokenStore.logoutTokens(
                        memberId,
                        accessToken,
                        5L
                );
                redisTemplate.opsForValue()
                        .set(
                                otherBlacklistKey,
                                "other",
                                5L,
                                TimeUnit.SECONDS
                        );

                assertThat(redisTokenStore.matchesRefreshToken(
                        memberId,
                        refreshToken
                )).isFalse();
                assertThat(redisTokenStore.isAccessTokenBlacklisted(
                        accessToken
                )).isTrue();
                assertThat(redisTemplate.hasKey(refreshKey(memberId)))
                        .isFalse();
                assertThat(redisTemplate.getExpire(blacklistKey))
                        .isPositive()
                        .isLessThanOrEqualTo(5L);

                redisTemplate.delete(blacklistKey);

                assertThat(redisTemplate.hasKey(otherBlacklistKey))
                        .isTrue();
            } finally {
                redisTemplate.delete(refreshKey(memberId));
                redisTemplate.delete(blacklistKey);
                redisTemplate.delete(otherBlacklistKey);
            }
        }
    }

    @Test
    void 실제_Redis에서_동일_RefreshToken_동시_Rotation은_하나만_성공한다() throws Exception {
        try (RedisFixture fixture = redisFixture()) {
            RedisTokenStore redisTokenStore = fixture.redisTokenStore();
            StringRedisTemplate redisTemplate = fixture.redisTemplate();
            Long memberId = Math.abs(UUID.randomUUID()
                    .getMostSignificantBits());
            String refreshToken = "refresh-token-" + UUID.randomUUID();
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executorService = Executors.newFixedThreadPool(2);

            try {
                redisTokenStore.saveRefreshToken(
                        memberId,
                        refreshToken,
                        10L
                );
                List<Future<Boolean>> futures = List.of(
                        executorService.submit(() -> rotateRefreshToken(
                                redisTokenStore,
                                memberId,
                                refreshToken,
                                "new-refresh-token-" + UUID.randomUUID(),
                                ready,
                                start
                        )),
                        executorService.submit(() -> rotateRefreshToken(
                                redisTokenStore,
                                memberId,
                                refreshToken,
                                "new-refresh-token-" + UUID.randomUUID(),
                                ready,
                                start
                        ))
                );

                assertThat(ready.await(
                        1,
                        TimeUnit.SECONDS
                )).isTrue();
                start.countDown();

                List<Boolean> results = new ArrayList<>();

                for (Future<Boolean> future : futures) {
                    results.add(future.get(
                            5,
                            TimeUnit.SECONDS
                    ));
                }

                assertThat(results)
                        .containsExactlyInAnyOrder(
                                true,
                                false
                        );
                assertThat(redisTokenStore.matchesRefreshToken(
                        memberId,
                        refreshToken
                )).isFalse();
            } finally {
                executorService.shutdownNow();
                redisTemplate.delete(refreshKey(memberId));
            }
        }
    }

    private boolean rotateRefreshToken(
            RedisTokenStore redisTokenStore,
            Long memberId,
            String currentRefreshToken,
            String newRefreshToken,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        start.await(
                1,
                TimeUnit.SECONDS
        );

        return redisTokenStore.rotateRefreshToken(
                memberId,
                currentRefreshToken,
                newRefreshToken,
                10L
        );
    }

    private RedisFixture redisFixture() {
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

        StringRedisTemplate redisTemplate =
                new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();

        return new RedisFixture(
                connectionFactory,
                redisTemplate,
                new RedisTokenStore(redisTemplate)
        );
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

    private record RedisFixture(
            LettuceConnectionFactory connectionFactory,
            StringRedisTemplate redisTemplate,
            RedisTokenStore redisTokenStore
    ) implements AutoCloseable {

        @SuppressWarnings("deprecation")
        @Override
        public void close() {
            connectionFactory.destroy();
        }
    }
}
