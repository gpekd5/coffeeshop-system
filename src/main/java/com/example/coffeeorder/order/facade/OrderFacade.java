package com.example.coffeeorder.order.facade;

import java.util.UUID;

import com.example.coffeeorder.common.exception.BusinessException;
import com.example.coffeeorder.common.exception.ErrorCode;
import com.example.coffeeorder.order.dto.response.OrderCreateResult;
import com.example.coffeeorder.order.service.OrderTransactionService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class OrderFacade {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

    private final OrderTransactionService orderTransactionService;

    public OrderFacade(OrderTransactionService orderTransactionService) {
        this.orderTransactionService = orderTransactionService;
    }

    public OrderCreateResult createOrder(
            Long memberId,
            String idempotencyKey
    ) {
        String normalizedIdempotencyKey =
                normalizeIdempotencyKey(idempotencyKey);

        try {
            return orderTransactionService.createOrder(
                    memberId,
                    normalizedIdempotencyKey
            );
        } catch (DataIntegrityViolationException exception) {
            return orderTransactionService.findProcessedOrder(
                    memberId,
                    normalizedIdempotencyKey
            );
        }
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
