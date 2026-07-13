package com.example.coffeeorder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.TimeZone;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class CoffeeOrderApplicationTests {

    @Autowired
    private Environment environment;

    @Test
    void 스프링_컨텍스트가_로딩된다() {
    }

    @Test
    void 테스트_환경_설정이_로딩된다() {
        assertEquals(
                "coffee-order-system",
                environment.getProperty("spring.application.name")
        );
        assertTrue(
                environment.getProperty(
                        "spring.datasource.url",
                        ""
                ).startsWith("jdbc:h2:mem:coffee_order_test")
        );
        assertEquals(
                "Asia/Seoul",
                TimeZone.getDefault().getID()
        );
    }
}
