package com.example.coffeeorder.event.kafka.consumer.service;

import java.util.Map;

import com.example.coffeeorder.common.exception.BusinessException;
import com.example.coffeeorder.common.exception.ErrorCode;
import com.example.coffeeorder.common.response.PageResponse;
import com.example.coffeeorder.event.kafka.consumer.dto.response.DeadLetterOrderEventResponse;
import com.example.coffeeorder.event.kafka.consumer.entity.DeadLetterOrderEvent;
import com.example.coffeeorder.event.kafka.consumer.repository.DeadLetterOrderEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeadLetterOrderEventQueryService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final String DEFAULT_SORT = "createdAt,desc";
    private static final Map<String, String> SORT_PROPERTIES = Map.ofEntries(
            Map.entry(
                    "id",
                    "id"
            ),
            Map.entry(
                    "eventId",
                    "eventId"
            ),
            Map.entry(
                    "topic",
                    "deadLetterTopic"
            ),
            Map.entry(
                    "originalTopic",
                    "originalTopic"
            ),
            Map.entry(
                    "deadLetterTopic",
                    "deadLetterTopic"
            ),
            Map.entry(
                    "kafkaPartition",
                    "kafkaPartition"
            ),
            Map.entry(
                    "kafkaOffset",
                    "kafkaOffset"
            ),
            Map.entry(
                    "receivedAt",
                    "receivedAt"
            ),
            Map.entry(
                    "createdAt",
                    "createdAt"
            ),
            Map.entry(
                    "updatedAt",
                    "updatedAt"
            )
    );

    private final DeadLetterOrderEventRepository deadLetterOrderEventRepository;

    public DeadLetterOrderEventQueryService(
            DeadLetterOrderEventRepository deadLetterOrderEventRepository
    ) {
        this.deadLetterOrderEventRepository = deadLetterOrderEventRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<DeadLetterOrderEventResponse> getDeadLetterOrderEvents(
            String page,
            String size,
            String sort
    ) {
        PageRequest pageRequest = createPageRequest(
                page,
                size,
                sort
        );
        Page<DeadLetterOrderEvent> events =
                deadLetterOrderEventRepository.findAll(pageRequest);

        return PageResponse.from(events.map(DeadLetterOrderEventResponse::from));
    }

    @Transactional(readOnly = true)
    public DeadLetterOrderEventResponse getDeadLetterOrderEvent(Long eventId) {
        DeadLetterOrderEvent event = deadLetterOrderEventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.DEAD_LETTER_ORDER_EVENT_NOT_FOUND
                ));

        return DeadLetterOrderEventResponse.from(event);
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
