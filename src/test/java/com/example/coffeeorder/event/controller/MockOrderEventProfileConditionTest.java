package com.example.coffeeorder.event.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MockOrderEventProfileConditionTest {

    @Test
    void local_프로필에서_활성화되면_Mock_API_컨트롤러를_등록한다() {
        contextRunner("local")
                .withPropertyValues("app.mock-order-event.enabled=true")
                .run(context -> assertThat(context)
                        .hasSingleBean(MockOrderEventController.class));
    }

    @Test
    void test_프로필에서_활성화되면_Mock_API_컨트롤러를_등록한다() {
        contextRunner("test")
                .withPropertyValues("app.mock-order-event.enabled=true")
                .run(context -> assertThat(context)
                        .hasSingleBean(MockOrderEventController.class));
    }

    @Test
    void prod_프로필에서는_활성화_설정이어도_Mock_API_컨트롤러를_등록하지_않는다() {
        contextRunner("prod")
                .withPropertyValues("app.mock-order-event.enabled=true")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(MockOrderEventController.class));
    }

    @Test
    void local_프로필에서도_활성화_설정이_없으면_Mock_API_컨트롤러를_등록하지_않는다() {
        contextRunner("local")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(MockOrderEventController.class));
    }

    private ApplicationContextRunner contextRunner(String profile) {
        return new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment()
                        .setActiveProfiles(profile))
                .withUserConfiguration(MockOrderEventController.class);
    }
}
