package com.example.coffeeorder.order.controller;

import com.example.coffeeorder.common.response.ApiResponse;
import com.example.coffeeorder.common.security.AuthMember;
import com.example.coffeeorder.order.dto.response.OrderCreateResponse;
import com.example.coffeeorder.order.facade.OrderFacade;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderFacade orderFacade;

    public OrderController(OrderFacade orderFacade) {
        this.orderFacade = orderFacade;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OrderCreateResponse>> createOrder(
            @AuthenticationPrincipal AuthMember authMember
    ) {
        OrderCreateResponse response = orderFacade.createOrder(
                authMember.memberId()
        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "주문과 결제가 완료되었습니다.",
                        response
                )
        );
    }
}
