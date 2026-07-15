package com.example.coffeeorder.order.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.example.coffeeorder.cart.entity.Cart;
import com.example.coffeeorder.cart.entity.CartItem;
import com.example.coffeeorder.cart.repository.CartItemRepository;
import com.example.coffeeorder.cart.repository.CartRepository;
import com.example.coffeeorder.common.exception.BusinessException;
import com.example.coffeeorder.common.exception.ErrorCode;
import com.example.coffeeorder.event.outbox.service.OutboxEventService;
import com.example.coffeeorder.member.entity.Member;
import com.example.coffeeorder.member.repository.MemberRepository;
import com.example.coffeeorder.menu.entity.Menu;
import com.example.coffeeorder.menu.repository.MenuRepository;
import com.example.coffeeorder.order.dto.response.OrderCreateResult;
import com.example.coffeeorder.order.dto.response.OrderCreateResponse;
import com.example.coffeeorder.order.entity.Order;
import com.example.coffeeorder.order.entity.OrderItem;
import com.example.coffeeorder.order.repository.OrderItemRepository;
import com.example.coffeeorder.order.repository.OrderRepository;
import com.example.coffeeorder.payment.entity.Payment;
import com.example.coffeeorder.payment.repository.PaymentRepository;
import com.example.coffeeorder.point.entity.Point;
import com.example.coffeeorder.point.entity.PointHistory;
import com.example.coffeeorder.point.repository.PointHistoryRepository;
import com.example.coffeeorder.point.repository.PointRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderTransactionService {

    private final MemberRepository memberRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final MenuRepository menuRepository;
    private final PointRepository pointRepository;
    private final PointHistoryRepository pointHistoryRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final OutboxEventService outboxEventService;
    private final Clock clock;

    public OrderTransactionService(
            MemberRepository memberRepository,
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            MenuRepository menuRepository,
            PointRepository pointRepository,
            PointHistoryRepository pointHistoryRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            PaymentRepository paymentRepository,
            OutboxEventService outboxEventService,
            Clock clock
    ) {
        this.memberRepository = memberRepository;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.menuRepository = menuRepository;
        this.pointRepository = pointRepository;
        this.pointHistoryRepository = pointHistoryRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentRepository = paymentRepository;
        this.outboxEventService = outboxEventService;
        this.clock = clock;
    }

    @Transactional
    public OrderCreateResult createOrder(
            Long memberId,
            String idempotencyKey
    ) {
        Optional<OrderCreateResult> existingResult =
                findExistingOrderResult(
                        memberId,
                        idempotencyKey
                );

        if (existingResult.isPresent()) {
            return existingResult.get();
        }

        Member member = findMember(memberId);
        Cart cart = findCartForUpdate(memberId);

        existingResult = findExistingOrderResult(
                memberId,
                idempotencyKey
        );

        if (existingResult.isPresent()) {
            return existingResult.get();
        }

        // Keep DB locks in cart -> menu IDs ascending -> point order to reduce deadlocks across instances.
        List<CartItem> cartItems = findCartItems(cart);
        List<Long> menuIds = extractMenuIds(cartItems);
        Map<Long, Menu> menusById = findMenusForOrder(menuIds);
        List<PreparedOrderItem> preparedItems = prepareOrderItems(
                cartItems,
                menusById
        );
        long totalAmount = calculateTotalAmount(preparedItems);
        Point point = findPointForUpdate(memberId);

        point.use(totalAmount);

        LocalDateTime now = LocalDateTime.now(clock);
        Order order = orderRepository.saveAndFlush(Order.completeWebCartOrder(
                member,
                idempotencyKey,
                totalAmount,
                now
        ));
        List<OrderItem> orderItems = orderItemRepository.saveAllAndFlush(
                createOrderItems(
                        order,
                        preparedItems
                )
        );
        Payment payment = paymentRepository.saveAndFlush(
                Payment.completePointPayment(
                        order,
                        member,
                        totalAmount,
                        now
                )
        );

        pointHistoryRepository.saveAndFlush(PointHistory.use(
                member,
                order.getId(),
                payment.getId(),
                totalAmount,
                point.getBalance()
        ));
        cartItemRepository.deleteAllByCart_Id(cart.getId());

        OrderCreateResponse response = OrderCreateResponse.of(
                order,
                orderItems,
                payment,
                totalAmount,
                point.getBalance()
        );
        outboxEventService.saveOrderCompletedEvent(
                memberId,
                response,
                now
        );

        return OrderCreateResult.created(response);
    }

    @Transactional(readOnly = true)
    public OrderCreateResult findProcessedOrder(
            Long memberId,
            String idempotencyKey
    ) {
        return findExistingOrderResult(
                memberId,
                idempotencyKey
        ).orElseThrow(() -> new BusinessException(
                ErrorCode.ORDER_PROCESSING_CONFLICT
        ));
    }

    private Optional<OrderCreateResult> findExistingOrderResult(
            Long memberId,
            String idempotencyKey
    ) {
        return orderRepository.findByMember_IdAndIdempotencyKey(
                        memberId,
                        idempotencyKey
                )
                .map(this::createAlreadyProcessedResult);
    }

    private OrderCreateResult createAlreadyProcessedResult(Order order) {
        List<OrderItem> orderItems =
                orderItemRepository.findAllByOrder_IdOrderByIdAsc(order.getId());
        Payment payment = paymentRepository.findByOrder_Id(order.getId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.PAYMENT_NOT_FOUND
                ));
        PointHistory pointHistory =
                pointHistoryRepository.findByOrderIdAndPaymentId(
                                order.getId(),
                                payment.getId()
                        )
                        .orElseThrow(() -> new BusinessException(
                                ErrorCode.INTERNAL_SERVER_ERROR
                        ));

        return OrderCreateResult.alreadyProcessed(
                OrderCreateResponse.of(
                        order,
                        orderItems,
                        payment,
                        payment.getAmount(),
                        pointHistory.getBalanceAfter()
                )
        );
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MEMBER_NOT_FOUND
                ));
    }

    private Cart findCartForUpdate(Long memberId) {
        return cartRepository.findByMemberIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.CART_NOT_FOUND
                ));
    }

    private List<CartItem> findCartItems(Cart cart) {
        List<CartItem> cartItems =
                cartItemRepository.findAllByCartIdWithMenuOrderByMenuIdAsc(
                        cart.getId()
                );

        if (cartItems.isEmpty()) {
            throw new BusinessException(ErrorCode.CART_EMPTY);
        }

        return cartItems;
    }

    private List<Long> extractMenuIds(List<CartItem> cartItems) {
        return cartItems.stream()
                .map(cartItem -> cartItem.getMenu()
                        .getId())
                .distinct()
                .sorted()
                .toList();
    }

    private Map<Long, Menu> findMenusForOrder(List<Long> menuIds) {
        List<Menu> menus = menuRepository.findAllByIdInForOrder(menuIds);

        if (menus.size() != menuIds.size()) {
            throw new BusinessException(ErrorCode.MENU_NOT_FOUND);
        }

        return menus.stream()
                .collect(Collectors.toMap(
                        Menu::getId,
                        Function.identity()
                ));
    }

    private List<PreparedOrderItem> prepareOrderItems(
            List<CartItem> cartItems,
            Map<Long, Menu> menusById
    ) {
        return cartItems.stream()
                .map(cartItem -> prepareOrderItem(
                        cartItem,
                        menusById
                ))
                .toList();
    }

    private PreparedOrderItem prepareOrderItem(
            CartItem cartItem,
            Map<Long, Menu> menusById
    ) {
        validateQuantity(cartItem);

        Menu menu = menusById.get(cartItem.getMenu()
                .getId());

        if (menu == null) {
            throw new BusinessException(ErrorCode.MENU_NOT_FOUND);
        }

        if (!menu.isOnSale()) {
            throw new BusinessException(ErrorCode.MENU_NOT_ON_SALE);
        }

        return new PreparedOrderItem(
                menu,
                cartItem.getQuantity()
        );
    }

    private void validateQuantity(CartItem cartItem) {
        if (cartItem.getQuantity() == null || cartItem.getQuantity() < 1) {
            throw new BusinessException(ErrorCode.INVALID_CART_ITEM_QUANTITY);
        }
    }

    private long calculateTotalAmount(List<PreparedOrderItem> preparedItems) {
        long totalAmount = 0L;

        for (PreparedOrderItem preparedItem : preparedItems) {
            totalAmount = addExact(
                    totalAmount,
                    multiplyExact(
                            preparedItem.menu()
                                    .getPrice(),
                            preparedItem.quantity()
                    )
            );
        }

        return totalAmount;
    }

    private List<OrderItem> createOrderItems(
            Order order,
            List<PreparedOrderItem> preparedItems
    ) {
        return preparedItems.stream()
                .map(preparedItem -> OrderItem.create(
                        order,
                        preparedItem.menu(),
                        preparedItem.quantity()
                ))
                .toList();
    }

    private Point findPointForUpdate(Long memberId) {
        return pointRepository.findByMemberIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.POINT_ACCOUNT_NOT_FOUND
                ));
    }

    private long multiplyExact(
            long unitPrice,
            int quantity
    ) {
        try {
            return Math.multiplyExact(
                    unitPrice,
                    quantity
            );
        } catch (ArithmeticException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private long addExact(
            long totalAmount,
            long lineAmount
    ) {
        try {
            return Math.addExact(
                    totalAmount,
                    lineAmount
            );
        } catch (ArithmeticException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private record PreparedOrderItem(
            Menu menu,
            int quantity
    ) {
    }
}
