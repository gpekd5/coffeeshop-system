package com.example.coffeeorder.event.kafka.consumer;

public class KafkaOrderEventProcessingException extends RuntimeException {

    public KafkaOrderEventProcessingException(String message) {
        super(message);
    }

    public KafkaOrderEventProcessingException(
            String message,
            Throwable cause
    ) {
        super(
                message,
                cause
        );
    }
}
