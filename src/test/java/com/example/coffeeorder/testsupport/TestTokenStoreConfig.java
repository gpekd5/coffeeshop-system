package com.example.coffeeorder.testsupport;

import com.example.coffeeorder.common.security.TokenStore;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestTokenStoreConfig {

    @Bean
    @Primary
    TokenStore tokenStore() {
        return new InMemoryTokenStore();
    }
}
