package com.example.coffeeorder.event.kafka.consumer.controller;

import com.example.coffeeorder.common.response.ApiResponse;
import com.example.coffeeorder.common.response.PageResponse;
import com.example.coffeeorder.event.kafka.consumer.dto.response.ProcessedKafkaEventResponse;
import com.example.coffeeorder.event.kafka.consumer.service.ProcessedKafkaEventQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/processed-kafka-events")
public class AdminProcessedKafkaEventController {

    private final ProcessedKafkaEventQueryService processedKafkaEventQueryService;

    public AdminProcessedKafkaEventController(
            ProcessedKafkaEventQueryService processedKafkaEventQueryService
    ) {
        this.processedKafkaEventQueryService = processedKafkaEventQueryService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProcessedKafkaEventResponse>>> getProcessedKafkaEvents(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) String sort
    ) {
        PageResponse<ProcessedKafkaEventResponse> response =
                processedKafkaEventQueryService.getProcessedKafkaEvents(
                        status,
                        page,
                        size,
                        sort
                );

        return ResponseEntity.ok(ApiResponse.success(
                "Kafka 이벤트 처리 이력 조회에 성공했습니다.",
                response
        ));
    }
}
