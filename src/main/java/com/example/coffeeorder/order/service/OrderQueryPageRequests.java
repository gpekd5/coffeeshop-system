package com.example.coffeeorder.order.service;

import java.util.Map;

import com.example.coffeeorder.common.util.PageRequestFactory;
import org.springframework.data.domain.PageRequest;

final class OrderQueryPageRequests {

    private static final String DEFAULT_SORT = "orderedAt,desc";
    private static final Map<String, String> SORT_PROPERTIES = Map.of(
            "orderId",
            "id",
            "orderNumber",
            "orderNumber",
            "status",
            "status",
            "orderChannel",
            "orderChannel",
            "totalAmount",
            "totalAmount",
            "orderedAt",
            "orderedAt",
            "memberId",
            "member.id",
            "memberEmail",
            "member.email"
    );

    private OrderQueryPageRequests() {
    }

    static PageRequest create(
            String page,
            String size,
            String sort
    ) {
        return PageRequestFactory.create(
                page,
                size,
                sort,
                DEFAULT_SORT,
                SORT_PROPERTIES
        );
    }
}
