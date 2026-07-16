package com.example.coffeeorder.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;

import com.example.coffeeorder.cart.entity.Cart;
import com.example.coffeeorder.cart.entity.CartItem;
import com.example.coffeeorder.cart.repository.CartItemRepository;
import com.example.coffeeorder.cart.repository.CartRepository;
import com.example.coffeeorder.event.outbox.repository.OutboxEventRepository;
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
import com.example.coffeeorder.point.entity.PointHistory;
import com.example.coffeeorder.point.entity.PointHistoryType;
import com.example.coffeeorder.point.repository.PointHistoryRepository;
import com.example.coffeeorder.point.repository.PointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import(OrderPointHistoryRollbackIntegrationTest.TestConfig.class)
class OrderPointHistoryRollbackIntegrationTest {

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
    void 포인트_이력_저장에_실패하면_주문_트랜잭션이_롤백된다() {
        Member member = 회원과_포인트를_생성한다(10_000L);
        장바구니에_메뉴를_담는다(member);

        assertThatThrownBy(() -> orderTransactionService.createOrder(
                member.getId(),
                UUID.randomUUID()
                        .toString()
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("point history save failed");

        assertThat(orderRepository.count()).isZero();
        assertThat(orderItemRepository.count()).isZero();
        assertThat(paymentRepository.count()).isZero();
        assertThat(pointHistoryRepository.count()).isZero();
        assertThat(outboxEventRepository.count()).isZero();
        assertThat(cartItemRepository.count()).isEqualTo(1);
        assertThat(pointRepository.findByMemberId(member.getId()))
                .hasValueSatisfying(point -> assertThat(point.getBalance())
                        .isEqualTo(10_000L));
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

    private void 장바구니에_메뉴를_담는다(Member member) {
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
                1
        ));
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        PointHistoryRepository failingPointHistoryRepository(
                @Qualifier("pointHistoryRepository")
                PointHistoryRepository delegate
        ) {
            return (PointHistoryRepository) Proxy.newProxyInstance(
                    PointHistoryRepository.class.getClassLoader(),
                    new Class<?>[] {PointHistoryRepository.class},
                    (
                            proxy,
                            method,
                            args
                    ) -> invokeRepository(
                            delegate,
                            method,
                            args
                    )
            );
        }

        private Object invokeRepository(
                PointHistoryRepository delegate,
                Method method,
                Object[] args
        ) throws Throwable {
            if (isUsePointHistorySaveAndFlush(
                    method,
                    args
            )) {
                throw new IllegalStateException("point history save failed");
            }

            try {
                return method.invoke(
                        delegate,
                        args
                );
            } catch (InvocationTargetException exception) {
                throw exception.getTargetException();
            }
        }

        private boolean isUsePointHistorySaveAndFlush(
                Method method,
                Object[] args
        ) {
            return method.getName()
                    .equals("saveAndFlush")
                    && args != null
                    && args.length == 1
                    && args[0] instanceof PointHistory history
                    && history.getType() == PointHistoryType.USE;
        }
    }
}
