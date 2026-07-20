package com.example.coffeeorder.event.service;

public enum OrderEventDeliveryMode {
    SYNC,
    OUTBOX;

    public boolean isSynchronous() {
        return this == SYNC;
    }

    public boolean storesOutboxEvent() {
        return this == OUTBOX;
    }
}
