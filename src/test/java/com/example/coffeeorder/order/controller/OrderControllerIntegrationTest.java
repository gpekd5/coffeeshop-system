package com.example.coffeeorder.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.jayway.jsonpath.JsonPath;
import com.example.coffeeorder.cart.entity.Cart;
import com.example.coffeeorder.cart.entity.CartItem;
import com.example.coffeeorder.cart.repository.CartItemRepository;
import com.example.coffeeorder.cart.repository.CartRepository;
import com.example.coffeeorder.common.security.JwtTokenProvider;
import com.example.coffeeorder.common.security.TokenStore;
import com.example.coffeeorder.member.entity.Member;
import com.example.coffeeorder.member.repository.MemberRepository;
import com.example.coffeeorder.menu.entity.Menu;
import com.example.coffeeorder.menu.entity.MenuCategory;
import com.example.coffeeorder.menu.entity.MenuStatus;
import com.example.coffeeorder.menu.repository.MenuRepository;
import com.example.coffeeorder.order.entity.Order;
import com.example.coffeeorder.order.entity.OrderChannel;
import com.example.coffeeorder.order.entity.OrderItem;
import com.example.coffeeorder.order.entity.OrderStatus;
import com.example.coffeeorder.order.repository.OrderItemRepository;
import com.example.coffeeorder.order.repository.OrderRepository;
import com.example.coffeeorder.payment.entity.Payment;
import com.example.coffeeorder.payment.entity.PaymentMethod;
import com.example.coffeeorder.payment.entity.PaymentStatus;
import com.example.coffeeorder.payment.repository.PaymentRepository;
import com.example.coffeeorder.point.entity.Point;
import com.example.coffeeorder.point.entity.PointHistory;
import com.example.coffeeorder.point.entity.PointHistoryType;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import(OrderControllerIntegrationTest.TestTokenStoreConfig.class)
class OrderControllerIntegrationTest {

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
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private TokenStore tokenStore;

    @BeforeEach
    void setUp() {
        pointHistoryRepository.deleteAll();
        paymentRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        pointRepository.deleteAll();
        menuRepository.deleteAll();
        memberRepository.deleteAll();

        if (tokenStore instanceof InMemoryTokenStore inMemoryTokenStore) {
            inMemoryTokenStore.clear();
        }
    }

    @Test
    void 인증되지_않은_사용자는_주문할_수_없다() throws Exception {
        mockMvc.perform(post("/api/v1/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 멱등키가_없으면_주문할_수_없다() throws Exception {
        TestUser user = 회원과_포인트와_토큰을_생성한다(
                "user@example.com",
                10_000L
        );

        mockMvc.perform(
                        post("/api/v1/orders")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + user.accessToken()
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void 멱등키가_UUID_형식이_아니면_주문할_수_없다() throws Exception {
        TestUser user = 회원과_포인트와_토큰을_생성한다(
                "user@example.com",
                10_000L
        );

        mockMvc.perform(
                        post("/api/v1/orders")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + user.accessToken()
                                )
                                .header(
                                        IDEMPOTENCY_KEY_HEADER,
                                        "not-a-uuid"
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 장바구니_상품을_서버_계산_금액으로_포인트_결제하고_장바구니를_비운다()
            throws Exception {
        TestUser user = 회원과_포인트와_토큰을_생성한다(
                "user@example.com",
                20_000L
        );
        Menu americano = 메뉴를_저장한다(
                "아메리카노",
                MenuCategory.COFFEE,
                4_500L,
                MenuStatus.ON_SALE
        );
        Menu croissant = 메뉴를_저장한다(
                "크루아상",
                MenuCategory.BAKERY,
                3_500L,
                MenuStatus.ON_SALE
        );
        Cart cart = 장바구니를_저장한다(user.member());
        장바구니_항목을_저장한다(
                cart,
                americano,
                2
        );
        장바구니_항목을_저장한다(
                cart,
                croissant,
                1
        );

        mockMvc.perform(
                        post("/api/v1/orders")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + user.accessToken()
                                )
                                .header(
                                        IDEMPOTENCY_KEY_HEADER,
                                        UUID.randomUUID().toString()
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("주문과 결제가 완료되었습니다."))
                .andExpect(jsonPath("$.data.orderId").isNumber())
                .andExpect(jsonPath("$.data.orderNumber").isString())
                .andExpect(jsonPath("$.data.orderChannel").value("WEB_CART"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].menuId").value(americano.getId()))
                .andExpect(jsonPath("$.data.items[0].menuName").value("아메리카노"))
                .andExpect(jsonPath("$.data.items[0].menuCategory").value("COFFEE"))
                .andExpect(jsonPath("$.data.items[0].unitPrice").value(4_500))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2))
                .andExpect(jsonPath("$.data.items[0].lineAmount").value(9_000))
                .andExpect(jsonPath("$.data.items[1].menuId").value(croissant.getId()))
                .andExpect(jsonPath("$.data.items[1].menuName").value("크루아상"))
                .andExpect(jsonPath("$.data.items[1].unitPrice").value(3_500))
                .andExpect(jsonPath("$.data.items[1].quantity").value(1))
                .andExpect(jsonPath("$.data.items[1].lineAmount").value(3_500))
                .andExpect(jsonPath("$.data.totalAmount").value(12_500))
                .andExpect(jsonPath("$.data.payment.paymentId").isNumber())
                .andExpect(jsonPath("$.data.payment.paymentMethod").value("POINT"))
                .andExpect(jsonPath("$.data.payment.paymentStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.payment.amount").value(12_500))
                .andExpect(jsonPath("$.data.payment.paidAt").isString())
                .andExpect(jsonPath("$.data.point.usedAmount").value(12_500))
                .andExpect(jsonPath("$.data.point.balanceAfter").value(7_500))
                .andExpect(jsonPath("$.data.orderedAt").isString());

        Point point = pointRepository.findByMemberId(user.member()
                        .getId())
                .orElseThrow();
        Order order = orderRepository.findAll()
                .getFirst();
        List<OrderItem> orderItems =
                orderItemRepository.findAllByOrder_IdOrderByIdAsc(order.getId());
        Payment payment = paymentRepository.findByOrder_Id(order.getId())
                .orElseThrow();
        PointHistory history = pointHistoryRepository.findAll()
                .getFirst();

        assertThat(point.getBalance()).isEqualTo(7_500L);
        assertThat(orderRepository.findAll()).hasSize(1);
        assertThat(order.getMemberId()).isEqualTo(user.member()
                .getId());
        assertThat(order.getOrderChannel()).isEqualTo(OrderChannel.WEB_CART);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(order.getTotalAmount()).isEqualTo(12_500L);
        assertThat(orderItems).hasSize(2);
        assertThat(payment.getPaymentMethod()).isEqualTo(PaymentMethod.POINT);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getAmount()).isEqualTo(12_500L);
        assertThat(history.getType()).isEqualTo(PointHistoryType.USE);
        assertThat(history.getAmount()).isEqualTo(-12_500L);
        assertThat(history.getOrderId()).isEqualTo(order.getId());
        assertThat(history.getPaymentId()).isEqualTo(payment.getId());
        assertThat(history.getBalanceAfter()).isEqualTo(7_500L);
        assertThat(cartItemRepository.findAllByCart_IdOrderByIdAsc(cart.getId()))
                .isEmpty();
    }

    @Test
    void 동일_멱등키_재요청은_기존_주문을_반환하고_추가_차감하지_않는다()
            throws Exception {
        TestUser user = 회원과_포인트와_토큰을_생성한다(
                "user@example.com",
                10_000L
        );
        Menu menu = 메뉴를_저장한다(
                "아메리카노",
                MenuCategory.COFFEE,
                4_500L,
                MenuStatus.ON_SALE
        );
        Cart cart = 장바구니를_저장한다(user.member());
        장바구니_항목을_저장한다(
                cart,
                menu,
                1
        );
        String idempotencyKey = UUID.randomUUID()
                .toString();

        mockMvc.perform(
                        post("/api/v1/orders")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + user.accessToken()
                                )
                                .header(
                                        IDEMPOTENCY_KEY_HEADER,
                                        idempotencyKey
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.totalAmount").value(4_500));

        Order order = orderRepository.findAll()
                .getFirst();

        mockMvc.perform(
                        post("/api/v1/orders")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + user.accessToken()
                                )
                                .header(
                                        IDEMPOTENCY_KEY_HEADER,
                                        idempotencyKey
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("ORDER_ALREADY_PROCESSED"))
                .andExpect(jsonPath("$.message").value("이미 처리된 주문입니다."))
                .andExpect(jsonPath("$.data.orderId").value(order.getId()))
                .andExpect(jsonPath("$.data.totalAmount").value(4_500))
                .andExpect(jsonPath("$.data.point.usedAmount").value(4_500))
                .andExpect(jsonPath("$.data.point.balanceAfter").value(5_500));

        Point point = pointRepository.findByMemberId(user.member()
                        .getId())
                .orElseThrow();

        assertThat(orderRepository.count()).isEqualTo(1L);
        assertThat(orderItemRepository.count()).isEqualTo(1L);
        assertThat(paymentRepository.count()).isEqualTo(1L);
        assertThat(pointHistoryRepository.count()).isEqualTo(1L);
        assertThat(point.getBalance()).isEqualTo(5_500L);
        assertThat(cartItemRepository.findAllByCart_IdOrderByIdAsc(cart.getId()))
                .isEmpty();
    }

    @Test
    void 서로_다른_회원은_같은_멱등키로_각각_주문할_수_있다() throws Exception {
        TestUser user = 회원과_포인트와_토큰을_생성한다(
                "user@example.com",
                10_000L
        );
        TestUser other = 회원과_포인트와_토큰을_생성한다(
                "other@example.com",
                10_000L
        );
        Menu menu = 메뉴를_저장한다(
                "아메리카노",
                MenuCategory.COFFEE,
                4_500L,
                MenuStatus.ON_SALE
        );
        장바구니_항목을_저장한다(
                장바구니를_저장한다(user.member()),
                menu,
                1
        );
        장바구니_항목을_저장한다(
                장바구니를_저장한다(other.member()),
                menu,
                1
        );
        String idempotencyKey = UUID.randomUUID()
                .toString();

        mockMvc.perform(
                        post("/api/v1/orders")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + user.accessToken()
                                )
                                .header(
                                        IDEMPOTENCY_KEY_HEADER,
                                        idempotencyKey
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        mockMvc.perform(
                        post("/api/v1/orders")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + other.accessToken()
                                )
                                .header(
                                        IDEMPOTENCY_KEY_HEADER,
                                        idempotencyKey
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        assertThat(orderRepository.count()).isEqualTo(2L);
        assertThat(paymentRepository.count()).isEqualTo(2L);
        assertThat(pointHistoryRepository.count()).isEqualTo(2L);
        assertThat(orderRepository.findAll())
                .extracting(Order::getIdempotencyKey)
                .containsOnly(idempotencyKey);
    }

    @Test
    void 동일_멱등키_동시_요청은_하나의_주문_결과만_생성한다() throws Exception {
        TestUser user = 회원과_포인트와_토큰을_생성한다(
                "user@example.com",
                10_000L
        );
        Menu menu = 메뉴를_저장한다(
                "아메리카노",
                MenuCategory.COFFEE,
                4_500L,
                MenuStatus.ON_SALE
        );
        Cart cart = 장바구니를_저장한다(user.member());
        장바구니_항목을_저장한다(
                cart,
                menu,
                1
        );
        String idempotencyKey = UUID.randomUUID()
                .toString();
        int requestCount = 5;
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(requestCount);

        try {
            List<Future<OrderRequestResult>> futures = new ArrayList<>();

            for (int i = 0; i < requestCount; i++) {
                futures.add(executorService.submit(() -> 주문_결과를_반환한다(
                        user.accessToken(),
                        idempotencyKey,
                        ready,
                        start
                )));
            }

            assertThat(ready.await(
                    1,
                    TimeUnit.SECONDS
            )).isTrue();
            start.countDown();

            List<OrderRequestResult> results = new ArrayList<>();

            for (Future<OrderRequestResult> future : futures) {
                results.add(future.get(
                        10,
                        TimeUnit.SECONDS
                ));
            }

            assertThat(results)
                    .extracting(OrderRequestResult::status)
                    .containsOnly(200);
            assertThat(results)
                    .extracting(OrderRequestResult::orderId)
                    .containsOnly(results.getFirst()
                            .orderId());
            assertThat(results)
                    .extracting(OrderRequestResult::code)
                    .contains("SUCCESS")
                    .allMatch(code -> code.equals("SUCCESS")
                            || code.equals("ORDER_ALREADY_PROCESSED"));
        } finally {
            executorService.shutdownNow();
        }

        Point point = pointRepository.findByMemberId(user.member()
                        .getId())
                .orElseThrow();

        assertThat(orderRepository.count()).isEqualTo(1L);
        assertThat(orderItemRepository.count()).isEqualTo(1L);
        assertThat(paymentRepository.count()).isEqualTo(1L);
        assertThat(pointHistoryRepository.count()).isEqualTo(1L);
        assertThat(point.getBalance()).isEqualTo(5_500L);
        assertThat(cartItemRepository.findAllByCart_IdOrderByIdAsc(cart.getId()))
                .isEmpty();
    }

    @Test
    void 주문_시점의_메뉴_가격과_이름으로_재검증하고_주문_항목에_스냅샷을_남긴다()
            throws Exception {
        TestUser user = 회원과_포인트와_토큰을_생성한다(
                "user@example.com",
                20_000L
        );
        Menu menu = 메뉴를_저장한다(
                "아메리카노",
                MenuCategory.COFFEE,
                4_500L,
                MenuStatus.ON_SALE
        );
        Cart cart = 장바구니를_저장한다(user.member());
        장바구니_항목을_저장한다(
                cart,
                menu,
                2
        );
        menu.update(
                "시그니처 아메리카노",
                null,
                null,
                5_000L
        );
        menuRepository.saveAndFlush(menu);

        mockMvc.perform(
                        post("/api/v1/orders")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + user.accessToken()
                                )
                                .header(
                                        IDEMPOTENCY_KEY_HEADER,
                                        UUID.randomUUID().toString()
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].menuName")
                        .value("시그니처 아메리카노"))
                .andExpect(jsonPath("$.data.items[0].unitPrice").value(5_000))
                .andExpect(jsonPath("$.data.items[0].lineAmount").value(10_000))
                .andExpect(jsonPath("$.data.totalAmount").value(10_000));

        OrderItem orderItem = orderItemRepository.findAll()
                .getFirst();
        menu.update(
                "주문 후 변경된 메뉴",
                null,
                null,
                7_000L
        );
        menu.changeStatus(MenuStatus.SOLD_OUT);
        menuRepository.saveAndFlush(menu);

        OrderItem savedOrderItem = orderItemRepository.findById(orderItem.getId())
                .orElseThrow();

        assertThat(savedOrderItem.getMenuName()).isEqualTo("시그니처 아메리카노");
        assertThat(savedOrderItem.getUnitPrice()).isEqualTo(5_000L);
        assertThat(savedOrderItem.getLineAmount()).isEqualTo(10_000L);
    }

    @Test
    void 포인트가_부족하면_주문_결제_이력_장바구니_삭제가_모두_롤백된다()
            throws Exception {
        TestUser user = 회원과_포인트와_토큰을_생성한다(
                "user@example.com",
                4_000L
        );
        Menu menu = 메뉴를_저장한다(
                "아메리카노",
                MenuCategory.COFFEE,
                4_500L,
                MenuStatus.ON_SALE
        );
        Cart cart = 장바구니를_저장한다(user.member());
        CartItem cartItem = 장바구니_항목을_저장한다(
                cart,
                menu,
                1
        );

        mockMvc.perform(
                        post("/api/v1/orders")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + user.accessToken()
                                )
                                .header(
                                        IDEMPOTENCY_KEY_HEADER,
                                        UUID.randomUUID().toString()
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("POINT_NOT_ENOUGH"));

        Point point = pointRepository.findByMemberId(user.member()
                        .getId())
                .orElseThrow();

        assertThat(point.getBalance()).isEqualTo(4_000L);
        assertThat(orderRepository.count()).isZero();
        assertThat(orderItemRepository.count()).isZero();
        assertThat(paymentRepository.count()).isZero();
        assertThat(pointHistoryRepository.count()).isZero();
        assertThat(cartItemRepository.findById(cartItem.getId())).isPresent();
    }

    @Test
    void 장바구니가_비어_있으면_주문할_수_없다() throws Exception {
        TestUser user = 회원과_포인트와_토큰을_생성한다(
                "user@example.com",
                10_000L
        );
        장바구니를_저장한다(user.member());

        mockMvc.perform(
                        post("/api/v1/orders")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + user.accessToken()
                                )
                                .header(
                                        IDEMPOTENCY_KEY_HEADER,
                                        UUID.randomUUID().toString()
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CART_EMPTY"));
    }

    @Test
    void 주문_시점에_판매중이_아닌_메뉴가_있으면_주문할_수_없고_롤백된다()
            throws Exception {
        TestUser user = 회원과_포인트와_토큰을_생성한다(
                "user@example.com",
                10_000L
        );
        Menu menu = 메뉴를_저장한다(
                "아메리카노",
                MenuCategory.COFFEE,
                4_500L,
                MenuStatus.ON_SALE
        );
        Cart cart = 장바구니를_저장한다(user.member());
        CartItem cartItem = 장바구니_항목을_저장한다(
                cart,
                menu,
                1
        );
        menu.changeStatus(MenuStatus.SOLD_OUT);
        menuRepository.saveAndFlush(menu);

        mockMvc.perform(
                        post("/api/v1/orders")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + user.accessToken()
                                )
                                .header(
                                        IDEMPOTENCY_KEY_HEADER,
                                        UUID.randomUUID().toString()
                                )
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("MENU_NOT_ON_SALE"));

        Point point = pointRepository.findByMemberId(user.member()
                        .getId())
                .orElseThrow();

        assertThat(point.getBalance()).isEqualTo(10_000L);
        assertThat(orderRepository.count()).isZero();
        assertThat(paymentRepository.count()).isZero();
        assertThat(pointHistoryRepository.count()).isZero();
        assertThat(cartItemRepository.findById(cartItem.getId())).isPresent();
    }

    @Test
    void 주문_시점에_삭제된_메뉴가_있으면_주문할_수_없고_롤백된다()
            throws Exception {
        TestUser user = 회원과_포인트와_토큰을_생성한다(
                "user@example.com",
                10_000L
        );
        Menu menu = 메뉴를_저장한다(
                "아메리카노",
                MenuCategory.COFFEE,
                4_500L,
                MenuStatus.ON_SALE
        );
        Cart cart = 장바구니를_저장한다(user.member());
        CartItem cartItem = 장바구니_항목을_저장한다(
                cart,
                menu,
                1
        );
        menu.delete(LocalDateTime.now());
        menuRepository.saveAndFlush(menu);

        mockMvc.perform(
                        post("/api/v1/orders")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + user.accessToken()
                                )
                                .header(
                                        IDEMPOTENCY_KEY_HEADER,
                                        UUID.randomUUID().toString()
                                )
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("MENU_NOT_FOUND"));

        Point point = pointRepository.findByMemberId(user.member()
                        .getId())
                .orElseThrow();

        assertThat(point.getBalance()).isEqualTo(10_000L);
        assertThat(orderRepository.count()).isZero();
        assertThat(paymentRepository.count()).isZero();
        assertThat(pointHistoryRepository.count()).isZero();
        assertThat(cartItemRepository.findById(cartItem.getId())).isPresent();
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

    private Menu 메뉴를_저장한다(
            String name,
            MenuCategory category,
            long price,
            MenuStatus status
    ) {
        return menuRepository.saveAndFlush(Menu.create(
                name,
                name + " 설명",
                category,
                price,
                status
        ));
    }

    private Cart 장바구니를_저장한다(Member member) {
        return cartRepository.saveAndFlush(Cart.create(member));
    }

    private CartItem 장바구니_항목을_저장한다(
            Cart cart,
            Menu menu,
            int quantity
    ) {
        return cartItemRepository.saveAndFlush(CartItem.create(
                cart,
                menu,
                quantity
        ));
    }

    private OrderRequestResult 주문_결과를_반환한다(
            String accessToken,
            String idempotencyKey,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        start.await(
                1,
                TimeUnit.SECONDS
        );

        MvcResult result = mockMvc.perform(
                        post("/api/v1/orders")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + accessToken
                                )
                                .header(
                                        IDEMPOTENCY_KEY_HEADER,
                                        idempotencyKey
                                )
                )
                .andReturn();

        int status = result.getResponse()
                .getStatus();
        String content = result.getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        if (status != 200) {
            return new OrderRequestResult(
                    status,
                    null,
                    null
            );
        }

        Number orderId = JsonPath.read(
                content,
                "$.data.orderId"
        );
        String code = JsonPath.read(
                content,
                "$.code"
        );

        return new OrderRequestResult(
                status,
                orderId.longValue(),
                code
        );
    }

    private record TestUser(
            Member member,
            String accessToken
    ) {
    }

    private record OrderRequestResult(
            int status,
            Long orderId,
            String code
    ) {
    }

    @TestConfiguration
    static class TestTokenStoreConfig {

        @Bean
        @Primary
        TokenStore tokenStore() {
            return new InMemoryTokenStore();
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
            if (ttlSeconds <= 0) {
                return false;
            }

            if (!currentRefreshToken.equals(refreshTokens.get(memberId))) {
                return false;
            }

            refreshTokens.put(
                    memberId,
                    newRefreshToken
            );

            return true;
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
            if (accessTokenTtlSeconds > 0) {
                blacklistedAccessTokens.add(accessToken);
            }

            refreshTokens.remove(memberId);
        }

        @Override
        public boolean isAccessTokenBlacklisted(String accessToken) {
            return blacklistedAccessTokens.contains(accessToken);
        }

        void clear() {
            refreshTokens.clear();
            blacklistedAccessTokens.clear();
        }
    }
}
