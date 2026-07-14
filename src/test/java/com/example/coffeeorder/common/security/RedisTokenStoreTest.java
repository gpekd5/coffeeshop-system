package com.example.coffeeorder.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisTokenStoreTest {

    private final StringRedisTemplate redisTemplate =
            mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations =
            mockValueOperations();
    private final RedisTokenStore redisTokenStore =
            new RedisTokenStore(redisTemplate);

    @Test
    void RefreshToken을_해시와_TTL로_Whitelist에_저장한다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ArgumentCaptor<String> tokenHashCaptor =
                ArgumentCaptor.forClass(String.class);

        redisTokenStore.saveRefreshToken(
                1L,
                "refresh-token",
                1209600L
        );

        verify(valueOperations).set(
                eq("auth:refresh:1"),
                tokenHashCaptor.capture(),
                eq(Duration.ofSeconds(1209600L))
        );
        assertThat(tokenHashCaptor.getValue())
                .isNotBlank()
                .isNotEqualTo("refresh-token");
    }

    @Test
    void 저장된_RefreshToken_해시가_일치하면_true를_반환한다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ArgumentCaptor<String> tokenHashCaptor =
                ArgumentCaptor.forClass(String.class);

        redisTokenStore.saveRefreshToken(
                1L,
                "refresh-token",
                1209600L
        );
        verify(valueOperations).set(
                eq("auth:refresh:1"),
                tokenHashCaptor.capture(),
                eq(Duration.ofSeconds(1209600L))
        );
        when(valueOperations.get("auth:refresh:1"))
                .thenReturn(tokenHashCaptor.getValue());

        boolean matched = redisTokenStore.matchesRefreshToken(
                1L,
                "refresh-token"
        );

        assertThat(matched).isTrue();
    }

    @Test
    void AccessToken을_해시_Key와_TTL로_Blacklist에_저장한다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ArgumentCaptor<String> keyCaptor =
                ArgumentCaptor.forClass(String.class);

        redisTokenStore.blacklistAccessToken(
                "access-token",
                1800L
        );

        verify(valueOperations).set(
                keyCaptor.capture(),
                eq("blacklisted"),
                eq(Duration.ofSeconds(1800L))
        );
        assertThat(keyCaptor.getValue())
                .startsWith("auth:blacklist:access:")
                .doesNotContain("access-token");
    }

    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> mockValueOperations() {
        return mock(ValueOperations.class);
    }
}
