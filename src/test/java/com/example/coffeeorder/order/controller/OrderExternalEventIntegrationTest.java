package com.example.coffeeorder.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.example.coffeeorder.cart.entity.Cart;
import com.example.coffeeorder.cart.entity.CartItem;
import com.example.coffeeorder.cart.repository.CartItemRepository;
import com.example.coffeeorder.cart.repository.CartRepository;
import com.example.coffeeorder.common.security.JwtTokenProvider;
import com.example.coffeeorder.common.security.TokenStore;
import com.example.coffeeorder.event.client.ExternalOrderEventClient;
import com.example.coffeeorder.event.client.ExternalOrderEventSendResult;
import com.example.coffeeorder.event.dto.OrderCompletedEventRequest;
import com.example.coffeeorder.event.outbox.entity.OutboxStatus;
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
import com.example.coffeeorder.point.entity.Point;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.external-order-event.enabled=true"
})
@Import(OrderExternalEventIntegrationTest.TestConfig.class)
class OrderExternalEventIntegrationTest {

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
    void 주문_생성은_외부_API를_직접_호출하지_않고_Outbox_이벤트를_저장한다()
            throws Exception {
        TestUser user = 회원과_포인트와_토큰을_생성한다(
                "user@example.com",
                10_000L
        );
        Menu menu = menuRepository.saveAndFlush(Menu.create(
                "아메리카노",
                "아메리카노 설명",
                MenuCategory.COFFEE,
                4_500L,
                MenuStatus.ON_SALE
        ));
        Cart cart = cartRepository.saveAndFlush(Cart.create(user.member()));
        cartItemRepository.saveAndFlush(CartItem.create(
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

        assertThat(externalOrderEventClient.requestCount()).isZero();
        assertThat(externalOrderEventLogRepository.findAll()).isEmpty();
        assertThat(outboxEventRepository.findAll())
                .hasSize(1)
                .first()
                .satisfies(event -> {
                    assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
                    assertThat(event.getAggregateId()).isEqualTo(orderRepository
                            .findAll()
                            .getFirst()
                            .getId());
                });
    }

    private TestUser 회원과_포인트와_토큰을_생성한다(
            String email,
            long balance
    ) {
        Member member = memberRepository.saveAndFlush(Member.create(
                email,
                "encrypted-password",
                email
        ));
        Point point = Point.create(member);

        if (balance > 0) {
            point.charge(balance);
        }

        pointRepository.saveAndFlush(point);

        String accessToken = jwtTokenProvider.createLoginTokens(member)
                .accessToken();

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
        TokenStore tokenStore() {
            return new InMemoryTokenStore();
        }

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

    static class InMemoryTokenStore implements TokenStore {

        private final Map<Long, String> refreshTokens =
                new ConcurrentHashMap<>();
        private final Set<String> blacklistedAccessTokens =
                ConcurrentHashMap.newKeySet();

        @Override
        public void saveRefreshToken(
                Long memberId,
                String refreshToken,
                long ttlSeconds
        ) {
            if (ttlSeconds > 0) {
                refreshTokens.put(
                        memberId,
                        refreshToken
                );
            }
        }

        @Override
        public boolean matchesRefreshToken(
                Long memberId,
                String refreshToken
        ) {
            return refreshToken.equals(refreshTokens.get(memberId));
        }

        @Override
        public boolean rotateRefreshToken(
                Long memberId,
                String currentRefreshToken,
                String newRefreshToken,
                long ttlSeconds
        ) {
            if (!matchesRefreshToken(
                    memberId,
                    currentRefreshToken
            )) {
                return false;
            }

            saveRefreshToken(
                    memberId,
                    newRefreshToken,
                    ttlSeconds
            );

            return true;
        }

        public void blacklistAccessToken(
                String accessToken,
                long ttlSeconds
        ) {
            if (ttlSeconds > 0) {
                blacklistedAccessTokens.add(accessToken);
            }
        }

        @Override
        public boolean isAccessTokenBlacklisted(String accessToken) {
            return blacklistedAccessTokens.contains(accessToken);
        }

        @Override
        public void deleteRefreshToken(Long memberId) {
            refreshTokens.remove(memberId);
        }

        @Override
        public void logoutTokens(
                Long memberId,
                String accessToken,
                long accessTokenTtlSeconds
        ) {
            deleteRefreshToken(memberId);
            blacklistAccessToken(
                    accessToken,
                    accessTokenTtlSeconds
            );
        }

        void clear() {
            refreshTokens.clear();
            blacklistedAccessTokens.clear();
        }
    }
}
