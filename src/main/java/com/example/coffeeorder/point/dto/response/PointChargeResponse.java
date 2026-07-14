package com.example.coffeeorder.point.dto.response;

import java.time.LocalDateTime;

import com.example.coffeeorder.point.entity.PointHistory;

public record PointChargeResponse(
        Long historyId,
        Long chargedAmount,
        Long balanceBefore,
        Long balanceAfter,
        LocalDateTime chargedAt
) {

    public static PointChargeResponse of(
            PointHistory history,
            long balanceBefore
    ) {
        return new PointChargeResponse(
                history.getId(),
                history.getAmount(),
                balanceBefore,
                history.getBalanceAfter(),
                history.getCreatedAt()
        );
    }
}
