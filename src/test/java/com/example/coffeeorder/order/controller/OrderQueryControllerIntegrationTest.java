package com.example.coffeeorder.order.controller;

import static com.example.coffeeorder.testsupport.IntegrationTestFixtures.memberWithRole;
import static com.example.coffeeorder.testsupport.IntegrationTestFixtures.menu;
import static com.example.coffeeorder.testsupport.TestAuthTokens.accessToken;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.example.coffeeorder.common.security.JwtTokenProvider;
import com.example.coffeeorder.common.security.TokenStore;
import com.example.coffeeorder.testsupport.InMemoryTokenStore;
import com.example.coffeeorder.testsupport.TestTokenStoreConfig;
import com.example.coffeeorder.member.entity.Member;
import com.example.coffeeorder.member.entity.MemberRole;
import com.example.coffeeorder.member.repository.MemberRepository;
import com.example.coffeeorder.menu.entity.Menu;
import com.example.coffeeorder.menu.entity.MenuCategory;
import com.example.coffeeorder.menu.entity.MenuStatus;
import com.example.coffeeorder.menu.repository.MenuRepository;
import com.example.coffeeorder.order.entity.Order;
import com.example.coffeeorder.order.entity.OrderItem;
import com.example.coffeeorder.order.repository.OrderItemRepository;
import com.example.coffeeorder.order.repository.OrderRepository;
import com.example.coffeeorder.payment.entity.Payment;
import com.example.coffeeorder.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import(TestTokenStoreConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OrderQueryControllerIntegrationTest {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MenuRepository menuRepository;

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
        paymentRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        menuRepository.deleteAll();
        memberRepository.deleteAll();

        if (tokenStore instanceof InMemoryTokenStore inMemoryTokenStore) {
            inMemoryTokenStore.clear();
        }
    }

    @Test
    void 사용자는_자신의_주문만_페이징_조회할_수_있다() throws Exception {
        TestMember member = 회원과_토큰을_발급한다(
                "member@example.com",
                "회원",
                MemberRole.USER
        );
        TestMember otherMember = 회원과_토큰을_발급한다(
                "other@example.com",
                "다른 회원",
                MemberRole.USER
        );
        Menu americano = 메뉴를_저장한다(
                "아메리카노",
                MenuCategory.COFFEE,
                4_500L
        );
        Menu cake = 메뉴를_저장한다(
                "치즈 케이크",
                MenuCategory.BAKERY,
                6_000L
        );

        주문을_저장한다(
                member.member(),
                LocalDateTime.of(
                        2026,
                        1,
                        1,
                        10,
                        0
                ),
                new OrderLine(
                        americano,
                        1
                )
        );
        SavedOrder latestOrder = 주문을_저장한다(
                member.member(),
                LocalDateTime.of(
                        2026,
                        1,
                        2,
                        10,
                        0
                ),
                new OrderLine(
                        americano,
                        1
                ),
                new OrderLine(
                        cake,
                        2
                )
        );
        주문을_저장한다(
                otherMember.member(),
                LocalDateTime.of(
                        2026,
                        1,
                        3,
                        10,
                        0
                ),
                new OrderLine(
                        cake,
                        1
                )
        );

        mockMvc.perform(
                        get("/api/v1/orders")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + member.accessToken()
                                )
                                .param(
                                        "page",
                                        "0"
                                )
                                .param(
                                        "size",
                                        "1"
                                )
                                .param(
                                        "sort",
                                        "orderedAt,desc"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("주문 목록 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.content[0].orderId").value(latestOrder.order()
                        .getId()))
                .andExpect(jsonPath("$.data.content[0].representativeMenuName").value("아메리카노"))
                .andExpect(jsonPath("$.data.content[0].additionalItemCount").value(1))
                .andExpect(jsonPath("$.data.content[0].totalAmount").value(16_500));
    }

    @Test
    void 주문_상세는_소유자에게_주문_항목_스냅샷과_결제_정보를_반환한다() throws Exception {
        TestMember member = 회원과_토큰을_발급한다(
                "detail@example.com",
                "상세 회원",
                MemberRole.USER
        );
        Menu latte = 메뉴를_저장한다(
                "카페 라떼",
                MenuCategory.COFFEE,
                5_500L
        );
        SavedOrder savedOrder = 주문을_저장한다(
                member.member(),
                LocalDateTime.of(
                        2026,
                        1,
                        4,
                        12,
                        30
                ),
                new OrderLine(
                        latte,
                        2
                )
        );

        latte.update(
                "변경된 라떼",
                "변경된 설명",
                MenuCategory.NON_COFFEE,
                9_900L
        );
        menuRepository.saveAndFlush(latte);

        mockMvc.perform(
                        get(
                                "/api/v1/orders/{orderId}",
                                savedOrder.order()
                                        .getId()
                        )
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + member.accessToken()
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("주문 상세 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.orderId").value(savedOrder.order()
                        .getId()))
                .andExpect(jsonPath("$.data.memberId").value(member.member()
                        .getId()))
                .andExpect(jsonPath("$.data.items[0].orderItemId").value(savedOrder.items()
                        .getFirst()
                        .getId()))
                .andExpect(jsonPath("$.data.items[0].menuName").value("카페 라떼"))
                .andExpect(jsonPath("$.data.items[0].menuCategory").value("COFFEE"))
                .andExpect(jsonPath("$.data.items[0].unitPrice").value(5_500))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2))
                .andExpect(jsonPath("$.data.items[0].lineAmount").value(11_000))
                .andExpect(jsonPath("$.data.payment.paymentId").value(savedOrder.payment()
                        .getId()))
                .andExpect(jsonPath("$.data.payment.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.payment.amount").value(11_000))
                .andExpect(jsonPath("$.data.totalAmount").value(11_000));
    }

    @Test
    void 다른_회원의_주문_상세는_조회할_수_없다() throws Exception {
        TestMember member = 회원과_토큰을_발급한다(
                "owner@example.com",
                "소유자",
                MemberRole.USER
        );
        TestMember otherMember = 회원과_토큰을_발급한다(
                "viewer@example.com",
                "조회자",
                MemberRole.USER
        );
        Menu menu = 메뉴를_저장한다(
                "콜드브루",
                MenuCategory.COFFEE,
                5_000L
        );
        SavedOrder savedOrder = 주문을_저장한다(
                member.member(),
                LocalDateTime.of(
                        2026,
                        1,
                        5,
                        10,
                        0
                ),
                new OrderLine(
                        menu,
                        1
                )
        );

        mockMvc.perform(
                        get(
                                "/api/v1/orders/{orderId}",
                                savedOrder.order()
                                        .getId()
                        )
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + otherMember.accessToken()
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ORDER_FORBIDDEN"));
    }

    @Test
    void 관리자는_전체_주문을_회원별로_페이징_조회할_수_있다() throws Exception {
        TestMember admin = 회원과_토큰을_발급한다(
                "admin@example.com",
                "관리자",
                MemberRole.ADMIN
        );
        TestMember firstMember = 회원과_토큰을_발급한다(
                "first@example.com",
                "첫 회원",
                MemberRole.USER
        );
        TestMember secondMember = 회원과_토큰을_발급한다(
                "second@example.com",
                "둘째 회원",
                MemberRole.USER
        );
        Menu menu = 메뉴를_저장한다(
                "바닐라 라떼",
                MenuCategory.COFFEE,
                6_000L
        );

        주문을_저장한다(
                firstMember.member(),
                LocalDateTime.of(
                        2026,
                        1,
                        6,
                        9,
                        0
                ),
                new OrderLine(
                        menu,
                        1
                )
        );
        SavedOrder latestOrder = 주문을_저장한다(
                firstMember.member(),
                LocalDateTime.of(
                        2026,
                        1,
                        7,
                        9,
                        0
                ),
                new OrderLine(
                        menu,
                        2
                )
        );
        주문을_저장한다(
                secondMember.member(),
                LocalDateTime.of(
                        2026,
                        1,
                        8,
                        9,
                        0
                ),
                new OrderLine(
                        menu,
                        1
                )
        );

        mockMvc.perform(
                        get("/api/v1/admin/orders")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + admin.accessToken()
                                )
                                .param(
                                        "memberId",
                                        String.valueOf(firstMember.member()
                                                .getId())
                                )
                                .param(
                                        "status",
                                        "COMPLETED"
                                )
                                .param(
                                        "orderChannel",
                                        "WEB_CART"
                                )
                                .param(
                                        "page",
                                        "0"
                                )
                                .param(
                                        "size",
                                        "10"
                                )
                                .param(
                                        "sort",
                                        "orderedAt,desc"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("전체 주문 목록 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].orderId").value(latestOrder.order()
                        .getId()))
                .andExpect(jsonPath("$.data.content[0].memberId").value(firstMember.member()
                        .getId()))
                .andExpect(jsonPath("$.data.content[0].memberEmail").value("first@example.com"))
                .andExpect(jsonPath("$.data.content[0].orderChannel").value("WEB_CART"))
                .andExpect(jsonPath("$.data.content[0].status").value("COMPLETED"));
    }

    @Test
    void 관리자는_주문_상세에서_회원과_스냅샷을_조회할_수_있다() throws Exception {
        TestMember admin = 회원과_토큰을_발급한다(
                "admin-detail@example.com",
                "관리자",
                MemberRole.ADMIN
        );
        TestMember member = 회원과_토큰을_발급한다(
                "member-detail@example.com",
                "상세 대상",
                MemberRole.USER
        );
        Menu menu = 메뉴를_저장한다(
                "디카페인 아메리카노",
                MenuCategory.DECAF,
                5_200L
        );
        SavedOrder savedOrder = 주문을_저장한다(
                member.member(),
                LocalDateTime.of(
                        2026,
                        1,
                        9,
                        11,
                        0
                ),
                new OrderLine(
                        menu,
                        1
                )
        );

        menu.update(
                "변경된 디카페인",
                "변경된 설명",
                MenuCategory.COFFEE,
                8_000L
        );
        menuRepository.saveAndFlush(menu);

        mockMvc.perform(
                        get(
                                "/api/v1/admin/orders/{orderId}",
                                savedOrder.order()
                                        .getId()
                        )
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + admin.accessToken()
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.member.memberId").value(member.member()
                        .getId()))
                .andExpect(jsonPath("$.data.member.email").value("member-detail@example.com"))
                .andExpect(jsonPath("$.data.member.name").value("상세 대상"))
                .andExpect(jsonPath("$.data.items[0].menuName").value("디카페인 아메리카노"))
                .andExpect(jsonPath("$.data.items[0].menuCategory").value("DECAF"))
                .andExpect(jsonPath("$.data.items[0].unitPrice").value(5_200))
                .andExpect(jsonPath("$.data.payment.status").value("COMPLETED"));
    }

    @Test
    void 일반_사용자는_관리자_주문_API를_호출할_수_없다() throws Exception {
        TestMember member = 회원과_토큰을_발급한다(
                "normal@example.com",
                "일반 회원",
                MemberRole.USER
        );

        mockMvc.perform(
                        get("/api/v1/admin/orders")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + member.accessToken()
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void 주문_목록_조회_파라미터가_올바르지_않으면_오류를_반환한다() throws Exception {
        TestMember member = 회원과_토큰을_발급한다(
                "invalid@example.com",
                "일반 회원",
                MemberRole.USER
        );
        TestMember admin = 회원과_토큰을_발급한다(
                "invalid-admin@example.com",
                "관리자",
                MemberRole.ADMIN
        );

        mockMvc.perform(
                        get("/api/v1/orders")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + member.accessToken()
                                )
                                .param(
                                        "status",
                                        "UNKNOWN"
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATUS"));

        mockMvc.perform(
                        get("/api/v1/admin/orders")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + admin.accessToken()
                                )
                                .param(
                                        "orderChannel",
                                        "UNKNOWN"
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_CHANNEL"));

        mockMvc.perform(
                        get("/api/v1/orders")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + member.accessToken()
                                )
                                .param(
                                        "page",
                                        "-1"
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_PAGE_REQUEST"));
    }

    private TestMember 회원과_토큰을_발급한다(
            String email,
            String name,
            MemberRole role
    ) {
        Member member = memberRepository.saveAndFlush(memberWithRole(
                email,
                name,
                role
        ));

        return new TestMember(
                member,
                accessToken(
                        jwtTokenProvider,
                        member
                )
        );
    }

    private Menu 메뉴를_저장한다(
            String name,
            MenuCategory category,
            long price
    ) {
        return menuRepository.saveAndFlush(menu(
                name,
                category,
                price,
                MenuStatus.ON_SALE
        ));
    }

    private SavedOrder 주문을_저장한다(
            Member member,
            LocalDateTime orderedAt,
            OrderLine... orderLines
    ) {
        long totalAmount = List.of(orderLines)
                .stream()
                .mapToLong(orderLine -> orderLine.menu()
                        .getPrice() * orderLine.quantity())
                .sum();
        Order order = orderRepository.saveAndFlush(Order.completeWebCartOrder(
                member,
                UUID.randomUUID()
                        .toString(),
                totalAmount,
                orderedAt
        ));
        List<OrderItem> items = orderItemRepository.saveAllAndFlush(
                List.of(orderLines)
                        .stream()
                        .map(orderLine -> OrderItem.create(
                                order,
                                orderLine.menu(),
                                orderLine.quantity()
                        ))
                        .toList()
        );
        Payment payment = paymentRepository.saveAndFlush(Payment.completePointPayment(
                order,
                member,
                totalAmount,
                orderedAt
        ));

        return new SavedOrder(
                order,
                items,
                payment
        );
    }

    private record TestMember(
            Member member,
            String accessToken
    ) {
    }

    private record OrderLine(
            Menu menu,
            int quantity
    ) {
    }

    private record SavedOrder(
            Order order,
            List<OrderItem> items,
            Payment payment
    ) {
    }
}
