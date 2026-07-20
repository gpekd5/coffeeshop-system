package com.example.coffeeorder.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static com.example.coffeeorder.testsupport.IntegrationTestFixtures.cart;
import static com.example.coffeeorder.testsupport.IntegrationTestFixtures.cartItem;
import static com.example.coffeeorder.testsupport.IntegrationTestFixtures.member;
import static com.example.coffeeorder.testsupport.IntegrationTestFixtures.menu;
import static com.example.coffeeorder.testsupport.IntegrationTestFixtures.point;
import static com.example.coffeeorder.testsupport.TestAuthTokens.accessToken;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.example.coffeeorder.cart.entity.Cart;
import com.example.coffeeorder.cart.repository.CartItemRepository;
import com.example.coffeeorder.cart.repository.CartRepository;
import com.example.coffeeorder.common.security.JwtTokenProvider;
import com.example.coffeeorder.common.security.TokenStore;
import com.example.coffeeorder.testsupport.InMemoryTokenStore;
import com.example.coffeeorder.testsupport.TestTokenStoreConfig;
import com.example.coffeeorder.event.client.ExternalOrderEventClient;
import com.example.coffeeorder.event.client.ExternalOrderEventSendResult;
import com.example.coffeeorder.event.dto.OrderCompletedEventRequest;
import com.example.coffeeorder.event.outbox.repository.OutboxEventRepository;
import com.example.coffeeorder.event.repository.ExternalOrderEventLogRepository;
import com.example.coffeeorder.member.entity.Member;
import com.example.coffeeorder.member.repository.MemberRepository;
import com.example.coffeeorder.menu.entity.Menu;
import com.example.coffeeorder.menu.entity.MenuCategory;
import com.example.coffeeorder.menu.entity.MenuStatus;
import com.example.coffeeorder.menu.repository.MenuRepository;
import com.example.coffeeorder.order.repository.OrderItemRepository;
import com.example.coffeeorder.order.repository.OrderRepository;
import com.example.coffeeorder.payment.repository.PaymentRepository;
import com.example.coffeeorder.point.repository.PointHistoryRepository;
import com.example.coffeeorder.point.repository.PointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.external-order-event.enabled=true",
        "app.order-event-delivery.mode=sync"
})
@Import({
        OrderSyncExternalEventIntegrationTest.TestConfig.class,
        TestTokenStoreConfig.class
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OrderSyncExternalEventIntegrationTest {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MenuRepository menuRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private PointRepository pointRepository;

    @Autowired
    private PointHistoryRepository pointHistoryRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ExternalOrderEventLogRepository externalOrderEventLogRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private TokenStore tokenStore;

    @Autowired
    private RecordingExternalOrderEventClient externalOrderEventClient;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        externalOrderEventLogRepository.deleteAll();
        pointHistoryRepository.deleteAll();
        paymentRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        pointRepository.deleteAll();
        menuRepository.deleteAll();
        memberRepository.deleteAll();
        externalOrderEventClient.reset();

        if (tokenStore instanceof InMemoryTokenStore inMemoryTokenStore) {
            inMemoryTokenStore.clear();
        }
    }

    @Test
    void sync_모드는_주문_Commit_이후_외부_API를_동기_호출하고_Outbox를_저장하지_않는다()
            throws Exception {
        TestUser user = 회원과_포인트와_토큰을_생성한다(
                "user@example.com",
                10_000L
        );
        Menu menu = menuRepository.saveAndFlush(menu(
                "아메리카노",
                MenuCategory.COFFEE,
                4_500L,
                MenuStatus.ON_SALE
        ));
        Cart cart = cartRepository.saveAndFlush(cart(user.member()));
        cartItemRepository.saveAndFlush(cartItem(
                cart,
                menu,
                1
        ));

        mockMvc.perform(
                        post("/api/v1/orders")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + user.accessToken()
                                )
                                .header(
                                        IDEMPOTENCY_KEY_HEADER,
                                        UUID.randomUUID()
                                                .toString()
                                )
                )
                .andExpect(status().isOk());

        assertThat(orderRepository.findAll()).hasSize(1);
        assertThat(externalOrderEventClient.requestCount()).isEqualTo(1);
        assertThat(externalOrderEventLogRepository.findAll()).hasSize(1);
        assertThat(outboxEventRepository.findAll()).isEmpty();
    }

    private TestUser 회원과_포인트와_토큰을_생성한다(
            String email,
            long balance
    ) {
        Member member = memberRepository.saveAndFlush(member(email));
        pointRepository.saveAndFlush(point(
                member,
                balance
        ));

        String accessToken = accessToken(
                jwtTokenProvider,
                member
        );

        return new TestUser(
                member,
                accessToken
        );
    }

    private record TestUser(
            Member member,
            String accessToken
    ) {
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        RecordingExternalOrderEventClient externalOrderEventClient() {
            return new RecordingExternalOrderEventClient();
        }
    }

    static class RecordingExternalOrderEventClient implements ExternalOrderEventClient {

        private final AtomicInteger requestCount = new AtomicInteger(0);

        @Override
        public ExternalOrderEventSendResult send(OrderCompletedEventRequest request) {
            requestCount.incrementAndGet();

            return ExternalOrderEventSendResult.success(
                    200,
                    LocalDateTime.now()
            );
        }

        void reset() {
            requestCount.set(0);
        }

        int requestCount() {
            return requestCount.get();
        }
    }
}
