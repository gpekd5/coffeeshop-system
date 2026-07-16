package com.example.coffeeorder.event.outbox.controller;

import com.example.coffeeorder.common.response.ApiResponse;
import com.example.coffeeorder.common.response.PageResponse;
import com.example.coffeeorder.event.outbox.dto.response.OutboxEventResponse;
import com.example.coffeeorder.event.outbox.entity.OutboxEvent;
import com.example.coffeeorder.event.outbox.service.OutboxEventService;
import com.example.coffeeorder.event.outbox.service.OutboxEventQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/outbox-events")
public class AdminOutboxEventController {

    private final OutboxEventQueryService outboxEventQueryService;
    private final OutboxEventService outboxEventService;

    public AdminOutboxEventController(
            OutboxEventQueryService outboxEventQueryService,
            OutboxEventService outboxEventService
    ) {
        this.outboxEventQueryService = outboxEventQueryService;
        this.outboxEventService = outboxEventService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<OutboxEventResponse>>> getOutboxEvents(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) String sort
    ) {
        PageResponse<OutboxEventResponse> response =
                outboxEventQueryService.getOutboxEvents(
                        status,
                        page,
                        size,
                        sort
                );

        return ResponseEntity.ok(ApiResponse.success(
                "Outbox 이벤트 목록 조회에 성공했습니다.",
                response
        ));
    }

    @PostMapping("/{eventId}/retry")
    public ResponseEntity<ApiResponse<OutboxEventResponse>> retryOutboxEvent(
            @PathVariable String eventId
    ) {
        OutboxEvent event = outboxEventService.resetFailedEventForRetry(eventId);

        return ResponseEntity.ok(ApiResponse.success(
                "Outbox 이벤트 재처리 요청에 성공했습니다.",
                OutboxEventResponse.from(event)
        ));
    }
}
