package com.example.coffeeorder.event.dto;

public record MockOrderEventResponse(
        boolean success,
        String message
) {

    public static MockOrderEventResponse accepted() {
        return new MockOrderEventResponse(
                true,
                "주문 이벤트를 수신했습니다."
        );
    }

    public static MockOrderEventResponse failure() {
        return failure("주문 이벤트 수신에 실패했습니다.");
    }

    public static MockOrderEventResponse failure(String message) {
        return new MockOrderEventResponse(
                false,
                message
        );
    }
}
