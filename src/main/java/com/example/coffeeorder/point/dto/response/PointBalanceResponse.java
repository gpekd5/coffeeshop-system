package com.example.coffeeorder.point.dto.response;

import java.time.LocalDateTime;

import com.example.coffeeorder.point.entity.Point;

public record PointBalanceResponse(
        Long memberId,
        Long balance,
        LocalDateTime updatedAt
) {

    public static PointBalanceResponse from(Point point) {
        return new PointBalanceResponse(
                point.getMemberId(),
                point.getBalance(),
                point.getUpdatedAt()
        );
    }
}
