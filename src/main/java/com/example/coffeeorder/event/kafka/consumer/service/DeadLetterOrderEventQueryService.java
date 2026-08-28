package com.example.coffeeorder.event.kafka.consumer.service;

import java.util.Map;

import com.example.coffeeorder.common.exception.BusinessException;
import com.example.coffeeorder.common.exception.ErrorCode;
import com.example.coffeeorder.common.response.PageResponse;
import com.example.coffeeorder.common.util.PageRequestFactory;
import com.example.coffeeorder.event.kafka.consumer.dto.response.DeadLetterOrderEventResponse;
import com.example.coffeeorder.event.kafka.consumer.entity.DeadLetterOrderEvent;
import com.example.coffeeorder.event.kafka.consumer.repository.DeadLetterOrderEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeadLetterOrderEventQueryService {

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
        PageRequest pageRequest = PageRequestFactory.create(
                page,
                size,
                sort,
                DEFAULT_SORT,
                SORT_PROPERTIES
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

}
