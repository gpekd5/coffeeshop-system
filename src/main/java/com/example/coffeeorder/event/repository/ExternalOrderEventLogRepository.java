package com.example.coffeeorder.event.repository;

import java.util.List;

import com.example.coffeeorder.event.entity.ExternalOrderEventLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalOrderEventLogRepository
        extends JpaRepository<ExternalOrderEventLog, Long> {

    List<ExternalOrderEventLog> findAllByOrderIdOrderByIdAsc(Long orderId);
}
