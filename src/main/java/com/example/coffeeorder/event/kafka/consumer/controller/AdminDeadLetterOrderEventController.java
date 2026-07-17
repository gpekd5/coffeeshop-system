package com.example.coffeeorder.event.kafka.consumer.controller;

import com.example.coffeeorder.common.response.ApiResponse;
import com.example.coffeeorder.common.response.PageResponse;
import com.example.coffeeorder.event.kafka.consumer.dto.response.DeadLetterOrderEventResponse;
import com.example.coffeeorder.event.kafka.consumer.service.DeadLetterOrderEventQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/dead-letter-order-events")
public class AdminDeadLetterOrderEventController {

    private final DeadLetterOrderEventQueryService deadLetterOrderEventQueryService;

    public AdminDeadLetterOrderEventController(
            DeadLetterOrderEventQueryService deadLetterOrderEventQueryService
    ) {
        this.deadLetterOrderEventQueryService = deadLetterOrderEventQueryService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<DeadLetterOrderEventResponse>>> getDeadLetterOrderEvents(
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) String sort
    ) {
        PageResponse<DeadLetterOrderEventResponse> response =
                deadLetterOrderEventQueryService.getDeadLetterOrderEvents(
                        page,
                        size,
                        sort
                );

        return ResponseEntity.ok(ApiResponse.success(
                "Dead Letter 주문 이벤트 목록 조회에 성공했습니다.",
                response
        ));
    }

    @GetMapping("/{deadLetterEventId}")
    public ResponseEntity<ApiResponse<DeadLetterOrderEventResponse>> getDeadLetterOrderEvent(
            @PathVariable Long deadLetterEventId
    ) {
        DeadLetterOrderEventResponse response =
                deadLetterOrderEventQueryService.getDeadLetterOrderEvent(
                        deadLetterEventId
                );

        return ResponseEntity.ok(ApiResponse.success(
                "Dead Letter 주문 이벤트 상세 조회에 성공했습니다.",
                response
        ));
    }
}
