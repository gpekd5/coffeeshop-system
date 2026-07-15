package com.example.coffeeorder.event.controller;

import com.example.coffeeorder.event.dto.MockOrderEventResponse;
import com.example.coffeeorder.event.dto.OrderCompletedEventRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mock/v1/order-events")
@Profile({
        "local",
        "test"
})
@ConditionalOnProperty(
        name = "app.mock-order-event.enabled",
        havingValue = "true"
)
public class MockOrderEventController {

    private static final long MAX_DELAY_MILLIS = 3_000L;
    private static final int ERROR_STATUS_CODE = 400;
    private static final int MIN_HTTP_STATUS_CODE = 100;
    private static final int MAX_HTTP_STATUS_CODE = 599;

    @PostMapping
    public ResponseEntity<MockOrderEventResponse> receiveOrderEvent(
            @RequestBody OrderCompletedEventRequest request,
            @RequestParam(defaultValue = "0") long delayMillis,
            @RequestParam(defaultValue = "200") int status
    ) throws InterruptedException {
        if (delayMillis < 0 || delayMillis > MAX_DELAY_MILLIS) {
            return ResponseEntity.badRequest()
                    .body(MockOrderEventResponse.failure(
                            "delayMillis는 0 이상 3000 이하만 허용됩니다."
                    ));
        }

        if (status < MIN_HTTP_STATUS_CODE || status > MAX_HTTP_STATUS_CODE) {
            return ResponseEntity.badRequest()
                    .body(MockOrderEventResponse.failure(
                            "status는 100 이상 599 이하만 허용됩니다."
                    ));
        }

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
