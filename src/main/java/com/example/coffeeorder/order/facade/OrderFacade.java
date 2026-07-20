package com.example.coffeeorder.order.facade;

import java.util.UUID;

import com.example.coffeeorder.common.exception.BusinessException;
import com.example.coffeeorder.common.exception.ErrorCode;
import com.example.coffeeorder.event.service.OrderEventDeliveryProperties;
import com.example.coffeeorder.event.service.OrderEventDeliveryService;
import com.example.coffeeorder.order.dto.response.OrderCreateResult;
import com.example.coffeeorder.order.service.OrderTransactionService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class OrderFacade {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

    private final OrderTransactionService orderTransactionService;
    private final OrderEventDeliveryService orderEventDeliveryService;
    private final OrderEventDeliveryProperties orderEventDeliveryProperties;

    public OrderFacade(
            OrderTransactionService orderTransactionService,
            OrderEventDeliveryService orderEventDeliveryService,
            OrderEventDeliveryProperties orderEventDeliveryProperties
    ) {
        this.orderTransactionService = orderTransactionService;
        this.orderEventDeliveryService = orderEventDeliveryService;
        this.orderEventDeliveryProperties = orderEventDeliveryProperties;
    }

    public OrderCreateResult createOrder(
            Long memberId,
            String idempotencyKey
    ) {
        String normalizedIdempotencyKey =
                normalizeIdempotencyKey(idempotencyKey);

        try {
            OrderCreateResult result = orderTransactionService.createOrder(
                    memberId,
                    normalizedIdempotencyKey,
                    orderEventDeliveryProperties.mode()
                            .storesOutboxEvent()
            );
            sendSynchronouslyIfNeeded(
                    memberId,
                    result
            );

            return result;
        } catch (DataIntegrityViolationException exception) {
            return orderTransactionService.findProcessedOrder(
                    memberId,
                    normalizedIdempotencyKey
            );
        }
    }

    private void sendSynchronouslyIfNeeded(
            Long memberId,
            OrderCreateResult result
    ) {
        if (!orderEventDeliveryProperties.mode()
                .isSynchronous() || result.alreadyProcessed()) {
            return;
        }

        orderEventDeliveryService.sendOrderCompletedEvent(
                memberId,
                result.response()
        );
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }

        String trimmedIdempotencyKey = idempotencyKey.trim();

        if (trimmedIdempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        try {
            return UUID.fromString(trimmedIdempotencyKey)
                    .toString();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
