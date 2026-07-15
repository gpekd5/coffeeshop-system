package com.example.coffeeorder.order.controller;

import com.example.coffeeorder.common.response.ApiResponse;
import com.example.coffeeorder.common.response.PageResponse;
import com.example.coffeeorder.order.dto.response.AdminOrderDetailResponse;
import com.example.coffeeorder.order.dto.response.AdminOrderSummaryResponse;
import com.example.coffeeorder.order.service.OrderQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/orders")
public class AdminOrderController {

    private final OrderQueryService orderQueryService;

    public AdminOrderController(OrderQueryService orderQueryService) {
        this.orderQueryService = orderQueryService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminOrderSummaryResponse>>> getOrders(
            @RequestParam(required = false) String memberId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String orderChannel,
            @RequestParam(required = false) String startDateTime,
            @RequestParam(required = false) String endDateTime,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) String sort
    ) {
        PageResponse<AdminOrderSummaryResponse> response =
                orderQueryService.getAdminOrders(
                        memberId,
                        status,
                        orderChannel,
                        startDateTime,
                        endDateTime,
                        page,
                        size,
                        sort
                );

        return ResponseEntity.ok(ApiResponse.success(
                "전체 주문 목록 조회에 성공했습니다.",
                response
        ));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<AdminOrderDetailResponse>> getOrder(
            @PathVariable Long orderId
    ) {
        AdminOrderDetailResponse response =
                orderQueryService.getAdminOrder(orderId);

        return ResponseEntity.ok(ApiResponse.success(
                "주문 상세 조회에 성공했습니다.",
                response
        ));
    }
}
