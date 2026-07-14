package com.example.coffeeorder.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

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
    void RefreshToken_Rotation은_Lua_Script_한_번으로_실행한다() {
        when(redisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any(),
                any(),
                any()
        )).thenReturn(1L);

        boolean rotated = redisTokenStore.rotateRefreshToken(
                1L,
                "old-refresh-token",
                "new-refresh-token",
                1209600L
        );

        assertThat(rotated).isTrue();
        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of("auth:refresh:1")),
                any(),
                any(),
                eq("1209600")
        );
    }

    @SuppressWarnings({
            "rawtypes",
            "unchecked"
    })
    @Test
    void 로그아웃은_Blacklist_저장과_RefreshToken_삭제를_Lua_Script_한_번으로_실행한다() {
        ArgumentCaptor<List> keysCaptor =
                ArgumentCaptor.forClass(List.class);

        redisTokenStore.logoutTokens(
                1L,
                "access-token",
                1800L
        );

        verify(redisTemplate).execute(
                any(RedisScript.class),
                keysCaptor.capture(),
                eq("blacklisted"),
                eq("1800")
        );
        assertThat(keysCaptor.getValue())
                .hasSize(2);
        assertThat(keysCaptor.getValue().get(0))
                .isEqualTo("auth:refresh:1");
        assertThat(keysCaptor.getValue().get(1).toString())
                .startsWith("auth:blacklist:access:")
                .doesNotContain("access-token");
    }

    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> mockValueOperations() {
        return mock(ValueOperations.class);
    }
}
