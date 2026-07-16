package com.example.coffeeorder.event.outbox.service;

import java.util.Map;

import com.example.coffeeorder.common.exception.BusinessException;
import com.example.coffeeorder.common.exception.ErrorCode;
import com.example.coffeeorder.common.response.PageResponse;
import com.example.coffeeorder.event.outbox.dto.response.OutboxEventResponse;
import com.example.coffeeorder.event.outbox.entity.OutboxEvent;
import com.example.coffeeorder.event.outbox.entity.OutboxStatus;
import com.example.coffeeorder.event.outbox.repository.OutboxEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxEventQueryService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
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
        PageRequest pageRequest = createPageRequest(
                page,
                size,
                sort
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

    private PageRequest createPageRequest(
            String page,
            String size,
            String sort
    ) {
        return PageRequest.of(
                parsePage(page),
                parseSize(size),
                parseSort(sort)
        );
    }

    private int parsePage(String page) {
        int parsedPage = parseInteger(
                page,
                DEFAULT_PAGE
        );

        if (parsedPage < 0) {
            throw new BusinessException(ErrorCode.INVALID_PAGE_REQUEST);
        }

        return parsedPage;
    }

    private int parseSize(String size) {
        int parsedSize = parseInteger(
                size,
                DEFAULT_SIZE
        );

        if (parsedSize <= 0) {
            throw new BusinessException(ErrorCode.INVALID_PAGE_REQUEST);
        }

        return parsedSize;
    }

    private int parseInteger(
            String value,
            int defaultValue
    ) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_PAGE_REQUEST);
        }
    }

    private Sort parseSort(String sort) {
        String sortExpression =
                sort == null || sort.isBlank() ? DEFAULT_SORT : sort.trim();
        String[] parts = sortExpression.split(",");

        if (parts.length > 2 || parts[0].isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PAGE_REQUEST);
        }

        String property = SORT_PROPERTIES.get(parts[0].trim());

        if (property == null) {
            throw new BusinessException(ErrorCode.INVALID_PAGE_REQUEST);
        }

        return Sort.by(
                parseDirection(parts),
                property
        );
    }

    private Sort.Direction parseDirection(String[] parts) {
        if (parts.length == 1 || parts[1].isBlank()) {
            return Sort.Direction.ASC;
        }

        try {
            return Sort.Direction.fromString(parts[1].trim());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_PAGE_REQUEST);
        }
    }
}
