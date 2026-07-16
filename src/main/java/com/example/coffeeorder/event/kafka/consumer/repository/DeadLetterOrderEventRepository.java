package com.example.coffeeorder.event.kafka.consumer.repository;

import com.example.coffeeorder.event.kafka.consumer.entity.DeadLetterOrderEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeadLetterOrderEventRepository
        extends JpaRepository<DeadLetterOrderEvent, Long> {
}
