package com.example.coffeeorder.order.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.example.coffeeorder.common.exception.BusinessException;
import com.example.coffeeorder.common.exception.ErrorCode;
import com.example.coffeeorder.event.service.OrderEventDeliveryMode;
import com.example.coffeeorder.event.service.OrderEventDeliveryProperties;
import com.example.coffeeorder.event.service.OrderEventDeliveryService;
import com.example.coffeeorder.order.dto.response.OrderCreateResponse;
import com.example.coffeeorder.order.dto.response.OrderCreateResult;
import com.example.coffeeorder.order.entity.OrderChannel;
import com.example.coffeeorder.order.entity.OrderStatus;
import com.example.coffeeorder.order.service.OrderTransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class OrderFacadeTest {

    @Mock
    private OrderTransactionService orderTransactionService;

    private OrderFacade orderFacade;

    @Mock
    private OrderEventDeliveryService orderEventDeliveryService;

    @BeforeEach
    void setUp() {
        orderFacade = orderFacade(OrderEventDeliveryMode.OUTBOX);
    }

    @Test
    void 신규_주문이_생성되면_주문_결과를_반환한다() {
        Long memberId = 1L;
        String idempotencyKey = UUID.randomUUID()
                .toString();
        OrderCreateResponse response = 주문_응답을_생성한다();
        OrderCreateResult expectedResult = OrderCreateResult.created(response);

        when(orderTransactionService.createOrder(
                memberId,
                idempotencyKey,
                true
        )).thenReturn(expectedResult);

        OrderCreateResult result = orderFacade.createOrder(
                memberId,
                idempotencyKey
        );

        assertThat(result).isSameAs(expectedResult);
        verifyNoInteractions(orderEventDeliveryService);
    }

    @Test
    void sync_모드는_Outbox를_저장하지_않고_Commit_이후_외부_이벤트를_동기_전송한다() {
        orderFacade = orderFacade(OrderEventDeliveryMode.SYNC);
        Long memberId = 1L;
        String idempotencyKey = UUID.randomUUID()
                .toString();
        OrderCreateResponse response = 주문_응답을_생성한다();
        OrderCreateResult expectedResult = OrderCreateResult.created(response);

        when(orderTransactionService.createOrder(
                memberId,
                idempotencyKey,
                false
        )).thenReturn(expectedResult);

        OrderCreateResult result = orderFacade.createOrder(
                memberId,
                idempotencyKey
        );

        assertThat(result).isSameAs(expectedResult);
        verify(orderEventDeliveryService).sendOrderCompletedEvent(
                memberId,
                response
        );
    }

    @Test
    void sync_모드라도_이미_처리된_주문은_외부_이벤트를_다시_전송하지_않는다() {
        orderFacade = orderFacade(OrderEventDeliveryMode.SYNC);
        Long memberId = 1L;
        String idempotencyKey = UUID.randomUUID()
                .toString();
        OrderCreateResult expectedResult =
                OrderCreateResult.alreadyProcessed(주문_응답을_생성한다());

        when(orderTransactionService.createOrder(
                memberId,
                idempotencyKey,
                false
        )).thenReturn(expectedResult);

        OrderCreateResult result = orderFacade.createOrder(
                memberId,
                idempotencyKey
        );

        assertThat(result).isSameAs(expectedResult);
        verify(orderEventDeliveryService, never()).sendOrderCompletedEvent(
                memberId,
                expectedResult.response()
        );
    }

    @Test
    void DB_Unique_충돌이_발생하면_기존_주문_결과를_반환한다() {
        Long memberId = 1L;
        String idempotencyKey = UUID.randomUUID()
                .toString()
                .toUpperCase();
        String normalizedIdempotencyKey = UUID.fromString(idempotencyKey)
                .toString();
        OrderCreateResult expectedResult = OrderCreateResult.alreadyProcessed(
                new OrderCreateResponse(
                        1L,
                        "ORD-20260714-000001",
                        OrderChannel.WEB_CART,
                        OrderStatus.COMPLETED,
                        List.of(),
                        4_500L,
                        null,
                        null,
                        LocalDateTime.now()
                )
        );

        when(orderTransactionService.createOrder(
                memberId,
                normalizedIdempotencyKey,
                true
        )).thenThrow(new DataIntegrityViolationException("duplicate"));
        when(orderTransactionService.findProcessedOrder(
                memberId,
                normalizedIdempotencyKey
        )).thenReturn(expectedResult);

        OrderCreateResult result = orderFacade.createOrder(
                memberId,
                idempotencyKey
        );

        assertThat(result).isSameAs(expectedResult);
        verify(orderTransactionService).findProcessedOrder(
                memberId,
                normalizedIdempotencyKey
        );
        verifyNoInteractions(orderEventDeliveryService);
    }

    @Test
    void 이미_처리된_주문이면_이미_처리된_결과를_그대로_반환한다() {
        Long memberId = 1L;
        String idempotencyKey = UUID.randomUUID()
                .toString();
        OrderCreateResult expectedResult =
                OrderCreateResult.alreadyProcessed(주문_응답을_생성한다());

        when(orderTransactionService.createOrder(
                memberId,
                idempotencyKey,
                true
        )).thenReturn(expectedResult);

        OrderCreateResult result = orderFacade.createOrder(
                memberId,
                idempotencyKey
        );

        assertThat(result).isSameAs(expectedResult);
        verifyNoInteractions(orderEventDeliveryService);
    }

    @Test
    void 멱등키가_없으면_주문할_수_없다() {
        assertThatThrownBy(() -> orderFacade.createOrder(
                1L,
                " "
        ))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REQUIRED)
                );
    }

    @Test
    void 멱등키가_UUID_형식이_아니면_주문할_수_없다() {
        assertThatThrownBy(() -> orderFacade.createOrder(
                1L,
                "not-a-uuid"
        ))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT)
                );
    }

    private OrderCreateResponse 주문_응답을_생성한다() {
        return new OrderCreateResponse(
                1L,
                "ORD-20260714-000001",
                OrderChannel.WEB_CART,
                OrderStatus.COMPLETED,
                List.of(),
                4_500L,
                null,
                null,
                LocalDateTime.now()
        );
    }

    private OrderFacade orderFacade(OrderEventDeliveryMode mode) {
        return new OrderFacade(
                orderTransactionService,
                orderEventDeliveryService,
                new OrderEventDeliveryProperties(mode)
        );
    }
}
