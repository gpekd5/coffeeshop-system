package com.example.coffeeorder.point.dto.response;

import java.time.LocalDateTime;

import com.example.coffeeorder.point.entity.PointHistory;
import com.example.coffeeorder.point.entity.PointHistoryType;

public record PointHistoryResponse(
        Long historyId,
        PointHistoryType type,
        Long amount,
        Long balanceAfter,
        Long orderId,
        LocalDateTime createdAt
) {

    public static PointHistoryResponse from(PointHistory history) {
        return new PointHistoryResponse(
                history.getId(),
                history.getType(),
                history.getAmount(),
                history.getBalanceAfter(),
                history.getOrderId(),
                history.getCreatedAt()
        );
    }
}
