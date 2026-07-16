package com.example.coffeeorder.event.kafka.producer;

import com.example.coffeeorder.event.outbox.entity.OutboxEvent;

public interface OrderCompletedEventProducer {

    void send(OutboxEvent event);
}
