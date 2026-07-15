package com.example.coffeeorder.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import com.example.coffeeorder.cart.entity.Cart;
import com.example.coffeeorder.cart.entity.CartItem;
import com.example.coffeeorder.cart.repository.CartItemRepository;
import com.example.coffeeorder.cart.repository.CartRepository;
import com.example.coffeeorder.event.outbox.entity.OutboxEvent;
import com.example.coffeeorder.event.outbox.entity.OutboxStatus;
import com.example.coffeeorder.event.outbox.repository.OutboxEventRepository;
import com.example.coffeeorder.member.entity.Member;
import com.example.coffeeorder.member.repository.MemberRepository;
import com.example.coffeeorder.menu.entity.Menu;
import com.example.coffeeorder.menu.entity.MenuCategory;
import com.example.coffeeorder.menu.entity.MenuStatus;
import com.example.coffeeorder.menu.repository.MenuRepository;
import com.example.coffeeorder.order.dto.response.OrderCreateResult;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class OrderOutboxIntegrationTest {

    @Autowired
    private OrderTransactionService orderTransactionService;

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
    private OutboxEventRepository outboxEventRepository;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        pointHistoryRepository.deleteAll();
        paymentRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        pointRepository.deleteAll();
        menuRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    void 주문이_성공하면_같은_트랜잭션에서_Outbox_Event를_저장한다() {
        Member member = 회원과_포인트를_생성한다(10_000L);
        장바구니에_메뉴를_담는다(
                member,
                2
        );

        OrderCreateResult result = orderTransactionService.createOrder(
                member.getId(),
                UUID.randomUUID()
                        .toString()
        );

        List<OutboxEvent> events = outboxEventRepository.findAll();

        assertThat(result.alreadyProcessed()).isFalse();
        assertThat(events).hasSize(1);

        OutboxEvent event = events.getFirst();
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getRetryCount()).isZero();
        assertThat(event.getAggregateType()).isEqualTo("ORDER");
        assertThat(event.getAggregateId()).isEqualTo(result.response()
                .orderId());
        assertThat(event.getEventType()).isEqualTo("ORDER_COMPLETED");
        assertThat(event.getPayload()).contains(
                "\"eventId\":\"" + event.getId() + "\"",
                "\"aggregateType\":\"ORDER\"",
                "\"aggregateId\":" + result.response()
                        .orderId(),
                "\"orderId\":" + result.response()
                        .orderId(),
                "\"memberId\":" + member.getId(),
                "\"paymentMethod\":\"POINT\""
        );
    }

    @Test
    void 이미_처리된_멱등_주문은_Outbox_Event를_중복_생성하지_않는다() {
        Member member = 회원과_포인트를_생성한다(10_000L);
        장바구니에_메뉴를_담는다(
                member,
                1
        );
        String idempotencyKey = UUID.randomUUID()
                .toString();

        OrderCreateResult createdResult = orderTransactionService.createOrder(
                member.getId(),
                idempotencyKey
        );
        OrderCreateResult alreadyProcessedResult =
                orderTransactionService.createOrder(
                        member.getId(),
                        idempotencyKey
                );

        assertThat(alreadyProcessedResult.alreadyProcessed()).isTrue();
        assertThat(alreadyProcessedResult.response()
                .orderId()).isEqualTo(createdResult.response()
                .orderId());
        assertThat(outboxEventRepository.findAll()).hasSize(1);
    }

    private Member 회원과_포인트를_생성한다(long balance) {
        Member member = memberRepository.saveAndFlush(Member.create(
                UUID.randomUUID() + "@example.com",
                "encrypted-password",
                "테스트회원"
        ));
        Point point = Point.create(member);
        point.charge(balance);
        pointRepository.saveAndFlush(point);

        return member;
    }

    private void 장바구니에_메뉴를_담는다(
            Member member,
            int quantity
    ) {
        Menu menu = menuRepository.saveAndFlush(Menu.create(
                "아메리카노",
                "아메리카노 설명",
                MenuCategory.COFFEE,
                4_500L,
                MenuStatus.ON_SALE
        ));
        Cart cart = cartRepository.saveAndFlush(Cart.create(member));
        cartItemRepository.saveAndFlush(CartItem.create(
                cart,
                menu,
                quantity
        ));
    }
}
