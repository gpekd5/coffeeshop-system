package com.example.coffeeorder.event.outbox.service;

import java.util.Map;

import com.example.coffeeorder.common.exception.BusinessException;
import com.example.coffeeorder.common.exception.ErrorCode;
import com.example.coffeeorder.common.response.PageResponse;
import com.example.coffeeorder.common.util.PageRequestFactory;
import com.example.coffeeorder.event.outbox.dto.response.OutboxEventResponse;
import com.example.coffeeorder.event.outbox.entity.OutboxEvent;
import com.example.coffeeorder.event.outbox.entity.OutboxStatus;
import com.example.coffeeorder.event.outbox.repository.OutboxEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxEventQueryService {

    private static final String DEFAULT_SORT = "createdAt,desc";
    private static final Map<String, String> SORT_PROPERTIES = Map.of(
            "eventId",
            "id",
            "aggregateType",
            "aggregateType",
            "aggregateId",
            "aggregateId",
            "eventType",
            "eventType",
            "status",
            "status",
            "retryCount",
            "retryCount",
            "nextRetryAt",
            "nextRetryAt",
            "publishedAt",
            "publishedAt",
            "createdAt",
            "createdAt",
            "updatedAt",
            "updatedAt"
    );

    private final OutboxEventRepository outboxEventRepository;

    public OutboxEventQueryService(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<OutboxEventResponse> getOutboxEvents(
            String status,
            String page,
            String size,
            String sort
    ) {
        OutboxStatus outboxStatus = parseStatus(status);
        PageRequest pageRequest = PageRequestFactory.create(
                page,
                size,
                sort,
                DEFAULT_SORT,
                SORT_PROPERTIES
        );
        Page<OutboxEvent> events = outboxStatus == null
                ? outboxEventRepository.findAll(pageRequest)
                : outboxEventRepository.findAllByStatus(
                        outboxStatus,
                        pageRequest
                );

        return PageResponse.from(events.map(OutboxEventResponse::from));
    }

    private OutboxStatus parseStatus(String status) {
        if (status == null) {
            return null;
        }

        if (status.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_OUTBOX_STATUS);
        }

        try {
            return OutboxStatus.valueOf(status.trim());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_OUTBOX_STATUS);
        }
    }

}
