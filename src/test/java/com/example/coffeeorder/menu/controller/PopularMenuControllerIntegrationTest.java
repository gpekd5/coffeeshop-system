package com.example.coffeeorder.menu.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import com.example.coffeeorder.member.entity.Member;
import com.example.coffeeorder.member.repository.MemberRepository;
import com.example.coffeeorder.menu.entity.Menu;
import com.example.coffeeorder.menu.entity.MenuCategory;
import com.example.coffeeorder.menu.entity.MenuStatus;
import com.example.coffeeorder.menu.repository.MenuRepository;
import com.example.coffeeorder.order.entity.Order;
import com.example.coffeeorder.order.entity.OrderItem;
import com.example.coffeeorder.order.entity.OrderStatus;
import com.example.coffeeorder.order.repository.OrderItemRepository;
import com.example.coffeeorder.order.repository.OrderRepository;
import com.example.coffeeorder.payment.entity.Payment;
import com.example.coffeeorder.payment.repository.PaymentRepository;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import(PopularMenuControllerIntegrationTest.FixedClockConfig.class)
class PopularMenuControllerIntegrationTest {

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(
            2026,
            7,
            15,
            10,
            0
    );

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

    private Member member;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        menuRepository.deleteAll();
        memberRepository.deleteAll();

        member = memberRepository.saveAndFlush(Member.create(
                "popular@example.com",
                "encrypted-password",
                "인기 메뉴 회원"
        ));
    }

    @Test
    void 최근_7일_완료_주문을_주문별_등장_횟수로_집계한다() throws Exception {
        Menu americano = 메뉴를_저장한다(
                "아메리카노",
                MenuCategory.COFFEE,
                4_500L,
                MenuStatus.ON_SALE
        );
        Menu latte = 메뉴를_저장한다(
                "카페라떼",
                MenuCategory.COFFEE,
                5_500L,
                MenuStatus.ON_SALE
        );
        Menu croissant = 메뉴를_저장한다(
                "크로와상",
                MenuCategory.BAKERY,
                3_500L,
                MenuStatus.ON_SALE
        );
        Menu smoothie = 메뉴를_저장한다(
                "딸기 스무디",
                MenuCategory.NON_COFFEE,
                6_000L,
                MenuStatus.ON_SALE
        );

        주문을_저장한다(
                OrderStatus.COMPLETED,
                NOW.minusDays(7),
                new OrderLine(
                        americano,
                        5
                )
        );
        주문을_저장한다(
                OrderStatus.COMPLETED,
                NOW.minusDays(1),
                new OrderLine(
                        americano,
                        1
                )
        );
        주문을_저장한다(
                OrderStatus.COMPLETED,
                NOW.minusHours(1),
                new OrderLine(
                        americano,
                        1
                )
        );
        주문을_저장한다(
                OrderStatus.COMPLETED,
                NOW.minusHours(2),
                new OrderLine(
                        croissant,
                        1
                )
        );
        주문을_저장한다(
                OrderStatus.COMPLETED,
                NOW.minusDays(2),
                new OrderLine(
                        croissant,
                        1
                )
        );
        주문을_저장한다(
                OrderStatus.COMPLETED,
                NOW.minusHours(3),
                new OrderLine(
                        latte,
                        1
                )
        );
        주문을_저장한다(
                OrderStatus.COMPLETED,
                NOW.minusDays(3),
                new OrderLine(
                        latte,
                        1
                )
        );
        주문을_저장한다(
                OrderStatus.COMPLETED,
                NOW.minusDays(7)
                        .minusSeconds(1),
                new OrderLine(
                        smoothie,
                        1
                )
        );
        주문을_저장한다(
                OrderStatus.CANCELLED,
                NOW.minusMinutes(30),
                new OrderLine(
                        smoothie,
                        10
                )
        );
        주문을_저장한다(
                OrderStatus.COMPLETED,
                NOW.plusSeconds(1),
                new OrderLine(
                        smoothie,
                        1
                )
        );

        latte.update(
                "카페라떼",
                "변경된 설명",
                MenuCategory.COFFEE,
                6_500L
        );
        latte.changeStatus(MenuStatus.SOLD_OUT);
        menuRepository.saveAndFlush(latte);

        mockMvc.perform(get("/api/v1/menus/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("인기 메뉴 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].rank").value(1))
                .andExpect(jsonPath("$.data[0].menuId").value(americano.getId()))
                .andExpect(jsonPath("$.data[0].menuName").value("아메리카노"))
                .andExpect(jsonPath("$.data[0].category").value("COFFEE"))
                .andExpect(jsonPath("$.data[0].currentPrice").value(4_500))
                .andExpect(jsonPath("$.data[0].currentStatus").value("ON_SALE"))
                .andExpect(jsonPath("$.data[0].orderCount").value(3))
                .andExpect(jsonPath("$.data[0].latestOrderedAt").value("2026-07-15T09:00:00"))
                .andExpect(jsonPath("$.data[1].rank").value(2))
                .andExpect(jsonPath("$.data[1].menuId").value(croissant.getId()))
                .andExpect(jsonPath("$.data[1].orderCount").value(2))
                .andExpect(jsonPath("$.data[1].latestOrderedAt").value("2026-07-15T08:00:00"))
                .andExpect(jsonPath("$.data[2].rank").value(3))
                .andExpect(jsonPath("$.data[2].menuId").value(latte.getId()))
                .andExpect(jsonPath("$.data[2].currentPrice").value(6_500))
                .andExpect(jsonPath("$.data[2].currentStatus").value("SOLD_OUT"))
                .andExpect(jsonPath("$.data[2].orderCount").value(2));
    }

    @Test
    void 주문_횟수와_최근_주문_시각이_같으면_메뉴_ID_오름차순으로_정렬한다() throws Exception {
        Menu firstMenu = 메뉴를_저장한다(
                "첫 메뉴",
                MenuCategory.COFFEE,
                4_000L,
                MenuStatus.ON_SALE
        );
        Menu secondMenu = 메뉴를_저장한다(
                "둘째 메뉴",
                MenuCategory.NON_COFFEE,
                5_000L,
                MenuStatus.ON_SALE
        );
        Menu thirdMenu = 메뉴를_저장한다(
                "셋째 메뉴",
                MenuCategory.BAKERY,
                3_000L,
                MenuStatus.ON_SALE
        );

        주문을_저장한다(
                OrderStatus.COMPLETED,
                NOW.minusHours(1),
                new OrderLine(
                        thirdMenu,
                        1
                )
        );
        주문을_저장한다(
                OrderStatus.COMPLETED,
                NOW.minusHours(1),
                new OrderLine(
                        secondMenu,
                        1
                )
        );
        주문을_저장한다(
                OrderStatus.COMPLETED,
                NOW.minusHours(1),
                new OrderLine(
                        firstMenu,
                        1
                )
        );

        mockMvc.perform(get("/api/v1/menus/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].rank").value(1))
                .andExpect(jsonPath("$.data[0].menuId").value(firstMenu.getId()))
                .andExpect(jsonPath("$.data[1].rank").value(2))
                .andExpect(jsonPath("$.data[1].menuId").value(secondMenu.getId()))
                .andExpect(jsonPath("$.data[2].rank").value(3))
                .andExpect(jsonPath("$.data[2].menuId").value(thirdMenu.getId()));
    }

    @Test
    void 삭제된_메뉴도_최근_완료_주문이면_집계된다() throws Exception {
        Menu deletedMenu = 메뉴를_저장한다(
                "시즌 케이크",
                MenuCategory.BAKERY,
                7_000L,
                MenuStatus.ON_SALE
        );
        주문을_저장한다(
                OrderStatus.COMPLETED,
                NOW.minusDays(1),
                new OrderLine(
                        deletedMenu,
                        1
                )
        );
        deletedMenu.delete(NOW);
        menuRepository.saveAndFlush(deletedMenu);

        mockMvc.perform(get("/api/v1/menus/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].rank").value(1))
                .andExpect(jsonPath("$.data[0].menuId").value(deletedMenu.getId()))
                .andExpect(jsonPath("$.data[0].menuName").value("시즌 케이크"))
                .andExpect(jsonPath("$.data[0].currentPrice").value(7_000))
                .andExpect(jsonPath("$.data[0].currentStatus").value("ON_SALE"))
                .andExpect(jsonPath("$.data[0].orderCount").value(1));
    }

    @Test
    void 집계_대상이_없으면_빈_배열을_반환한다() throws Exception {
        Menu menu = 메뉴를_저장한다(
                "오래된 메뉴",
                MenuCategory.ETC,
                1_000L,
                MenuStatus.ON_SALE
        );
        주문을_저장한다(
                OrderStatus.COMPLETED,
                NOW.minusDays(7)
                        .minusSeconds(1),
                new OrderLine(
                        menu,
                        1
                )
        );
        주문을_저장한다(
                OrderStatus.CANCELLED,
                NOW.minusDays(1),
                new OrderLine(
                        menu,
                        1
                )
        );

        mockMvc.perform(get("/api/v1/menus/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("인기 메뉴 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.length()").value(0));
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

    private void 주문을_저장한다(
            OrderStatus status,
            LocalDateTime orderedAt,
            OrderLine... orderLines
    ) {
        long totalAmount = List.of(orderLines)
                .stream()
                .mapToLong(orderLine -> orderLine.menu()
                        .getPrice() * orderLine.quantity())
                .sum();
        Order order = Order.completeWebCartOrder(
                member,
                UUID.randomUUID()
                        .toString(),
                totalAmount,
                orderedAt
        );
        ReflectionTestUtils.setField(
                order,
                "status",
                status
        );
        Order savedOrder = orderRepository.saveAndFlush(order);

        orderItemRepository.saveAllAndFlush(List.of(orderLines)
                .stream()
                .map(orderLine -> OrderItem.create(
                        savedOrder,
                        orderLine.menu(),
                        orderLine.quantity()
                ))
                .toList());
        paymentRepository.saveAndFlush(Payment.completePointPayment(
                savedOrder,
                member,
                totalAmount,
                orderedAt
        ));
    }

    private record OrderLine(
            Menu menu,
            int quantity
    ) {
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(
                    NOW.atZone(ZONE_ID)
                            .toInstant(),
                    ZONE_ID
            );
        }
    }
}
