package com.example.coffeeorder.event.kafka.consumer.service;

import java.util.Map;

import com.example.coffeeorder.common.exception.ErrorCode;
import com.example.coffeeorder.common.response.PageResponse;
import com.example.coffeeorder.common.util.EnumRequestParser;
import com.example.coffeeorder.common.util.PageRequestFactory;
import com.example.coffeeorder.event.kafka.consumer.dto.response.ProcessedKafkaEventResponse;
import com.example.coffeeorder.event.kafka.consumer.entity.KafkaEventProcessingStatus;
import com.example.coffeeorder.event.kafka.consumer.entity.ProcessedKafkaEvent;
import com.example.coffeeorder.event.kafka.consumer.repository.ProcessedKafkaEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProcessedKafkaEventQueryService {

    private static final String DEFAULT_SORT = "createdAt,desc";
    private static final Map<String, String> SORT_PROPERTIES = Map.ofEntries(
            Map.entry(
                    "eventId",
                    "eventId"
            ),
            Map.entry(
                    "eventType",
                    "eventType"
            ),
            Map.entry(
                    "status",
                    "status"
            ),
            Map.entry(
                    "topic",
                    "topic"
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
                    "attemptCount",
                    "attemptCount"
            ),
            Map.entry(
                    "processingDeadlineAt",
                    "processingDeadlineAt"
            ),
            Map.entry(
                    "processedAt",
                    "processedAt"
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

    private final ProcessedKafkaEventRepository processedKafkaEventRepository;

    public ProcessedKafkaEventQueryService(
            ProcessedKafkaEventRepository processedKafkaEventRepository
    ) {
        this.processedKafkaEventRepository = processedKafkaEventRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProcessedKafkaEventResponse> getProcessedKafkaEvents(
            String status,
            String page,
            String size,
            String sort
    ) {
        KafkaEventProcessingStatus processingStatus = EnumRequestParser.parseOptional(
                status,
                KafkaEventProcessingStatus.class,
                ErrorCode.INVALID_KAFKA_EVENT_PROCESSING_STATUS
        );
        PageRequest pageRequest = PageRequestFactory.create(
                page,
                size,
                sort,
                DEFAULT_SORT,
                SORT_PROPERTIES
        );
        Page<ProcessedKafkaEvent> events = processingStatus == null
                ? processedKafkaEventRepository.findAll(pageRequest)
                : processedKafkaEventRepository.findAllByStatus(
                        processingStatus,
                        pageRequest
                );

        return PageResponse.from(events.map(ProcessedKafkaEventResponse::from));
    }

}
