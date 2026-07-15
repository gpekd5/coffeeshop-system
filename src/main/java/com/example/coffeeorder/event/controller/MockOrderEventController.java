package com.example.coffeeorder.event.controller;

import com.example.coffeeorder.event.dto.MockOrderEventResponse;
import com.example.coffeeorder.event.dto.OrderCompletedEventRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mock/v1/order-events")
@ConditionalOnProperty(
        name = "app.mock-order-event.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class MockOrderEventController {

    private static final int ERROR_STATUS_CODE = 400;

    @PostMapping
    public ResponseEntity<MockOrderEventResponse> receiveOrderEvent(
            @RequestBody OrderCompletedEventRequest request,
            @RequestParam(defaultValue = "0") long delayMillis,
            @RequestParam(defaultValue = "200") int status
    ) throws InterruptedException {
        sleep(delayMillis);

        if (status >= ERROR_STATUS_CODE) {
            return ResponseEntity.status(status)
                    .body(MockOrderEventResponse.failure());
        }

        return ResponseEntity.status(status)
                .body(MockOrderEventResponse.accepted());
    }

    private void sleep(long delayMillis) throws InterruptedException {
        if (delayMillis <= 0) {
            return;
        }

        Thread.sleep(delayMillis);
    }
}
