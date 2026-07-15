package com.example.coffeeorder.order.controller;

import com.example.coffeeorder.common.exception.ErrorCode;
import com.example.coffeeorder.common.response.ApiResponse;
import com.example.coffeeorder.common.response.PageResponse;
import com.example.coffeeorder.common.security.AuthMember;
import com.example.coffeeorder.order.dto.response.OrderCreateResult;
import com.example.coffeeorder.order.dto.response.OrderCreateResponse;
import com.example.coffeeorder.order.dto.response.OrderDetailResponse;
import com.example.coffeeorder.order.dto.response.OrderSummaryResponse;
import com.example.coffeeorder.order.facade.OrderFacade;
import com.example.coffeeorder.order.service.OrderQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderFacade orderFacade;
    private final OrderQueryService orderQueryService;

    public OrderController(
            OrderFacade orderFacade,
            OrderQueryService orderQueryService
    ) {
        this.orderFacade = orderFacade;
        this.orderQueryService = orderQueryService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<OrderSummaryResponse>>> getMyOrders(
            @AuthenticationPrincipal AuthMember authMember,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) String sort
    ) {
        PageResponse<OrderSummaryResponse> response =
                orderQueryService.getMyOrders(
                        authMember.memberId(),
                        status,
                        page,
                        size,
                        sort
                );

        return ResponseEntity.ok(ApiResponse.success(
                "주문 목록 조회에 성공했습니다.",
                response
        ));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> getMyOrder(
            @AuthenticationPrincipal AuthMember authMember,
            @PathVariable Long orderId
    ) {
        OrderDetailResponse response = orderQueryService.getMyOrder(
                authMember.memberId(),
                orderId
        );

        return ResponseEntity.ok(ApiResponse.success(
                "주문 상세 조회에 성공했습니다.",
                response
        ));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OrderCreateResponse>> createOrder(
            @AuthenticationPrincipal AuthMember authMember,
            @RequestHeader(
                    value = "Idempotency-Key",
                    required = false
            ) String idempotencyKey
    ) {
        OrderCreateResult result = orderFacade.createOrder(
                authMember.memberId(),
                idempotencyKey
        );

        if (result.alreadyProcessed()) {
            ErrorCode errorCode = ErrorCode.ORDER_ALREADY_PROCESSED;

            return ResponseEntity.ok(
                    ApiResponse.success(
                            errorCode.getCode(),
                            errorCode.getMessage(),
                            result.response()
                    )
            );
        }

        return ResponseEntity.ok(
                ApiResponse.success(
                        "주문과 결제가 완료되었습니다.",
                        result.response()
                )
        );
    }
}
