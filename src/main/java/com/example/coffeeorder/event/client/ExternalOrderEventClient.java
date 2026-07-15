package com.example.coffeeorder.event.client;

import com.example.coffeeorder.event.dto.OrderCompletedEventRequest;

public interface ExternalOrderEventClient {

    ExternalOrderEventSendResult send(OrderCompletedEventRequest request);
}
