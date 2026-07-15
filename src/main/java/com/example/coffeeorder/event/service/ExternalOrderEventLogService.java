package com.example.coffeeorder.event.service;

import com.example.coffeeorder.event.client.ExternalOrderEventSendResult;
import com.example.coffeeorder.event.dto.OrderCompletedEventRequest;
import com.example.coffeeorder.event.entity.ExternalOrderEventLog;
import com.example.coffeeorder.event.repository.ExternalOrderEventLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExternalOrderEventLogService {

    private final ExternalOrderEventLogRepository externalOrderEventLogRepository;

    public ExternalOrderEventLogService(
            ExternalOrderEventLogRepository externalOrderEventLogRepository
    ) {
        this.externalOrderEventLogRepository = externalOrderEventLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            OrderCompletedEventRequest event,
            ExternalOrderEventSendResult result
    ) {
        externalOrderEventLogRepository.saveAndFlush(ExternalOrderEventLog.of(
                event,
                result
        ));
    }
}
