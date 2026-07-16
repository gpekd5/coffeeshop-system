package com.example.coffeeorder.event.kafka.consumer.repository;

import java.util.Optional;

import com.example.coffeeorder.event.kafka.consumer.entity.KafkaEventProcessingStatus;
import com.example.coffeeorder.event.kafka.consumer.entity.ProcessedKafkaEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessedKafkaEventRepository
        extends JpaRepository<ProcessedKafkaEvent, String> {

    Page<ProcessedKafkaEvent> findAllByStatus(
            KafkaEventProcessingStatus status,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select event
            from ProcessedKafkaEvent event
            where event.eventId = :eventId
            """)
    Optional<ProcessedKafkaEvent> findByEventIdForUpdate(
            @Param("eventId") String eventId
    );
}
