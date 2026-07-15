package com.example.coffeeorder.event.outbox.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.example.coffeeorder.common.exception.BusinessException;
import com.example.coffeeorder.common.exception.ErrorCode;
import com.example.coffeeorder.event.outbox.dto.OrderCompletedOutboxPayload;
import com.example.coffeeorder.event.outbox.entity.OutboxEvent;
import com.example.coffeeorder.event.outbox.entity.OutboxStatus;
import com.example.coffeeorder.event.outbox.repository.OutboxEventRepository;
import com.example.coffeeorder.order.dto.response.OrderCreateResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class OutboxEventService {

    private static final int MAX_RETRY_COUNT = 3;
    private static final int MIN_FETCH_LIMIT = 1;
    private static final long BASE_RETRY_DELAY_SECONDS = 60L;

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxEventService(
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent saveOrderCompletedEvent(
            Long memberId,
            OrderCreateResponse order,
            LocalDateTime occurredAt
    ) {
        String eventId = UUID.randomUUID()
                .toString();
        OrderCompletedOutboxPayload payload =
                OrderCompletedOutboxPayload.from(
                        eventId,
                        memberId,
                        order,
                        occurredAt
                );

        OutboxEvent event = OutboxEvent.orderCompleted(
                eventId,
                order.orderId(),
                serialize(payload)
        );

        return outboxEventRepository.saveAndFlush(event);
    }

    @Transactional
    public List<OutboxEvent> findPublishableEventsForUpdate(
            LocalDateTime now,
            int limit
    ) {
        validateLimit(limit);

        return outboxEventRepository.findPublishableEventsForUpdate(
                now,
                OutboxStatus.PENDING,
                OutboxStatus.FAILED,
                PageRequest.of(
                        0,
                        limit
                )
        );
    }

    @Transactional
    public List<OutboxEvent> reservePublishableEventsForPublish(
            LocalDateTime now,
            int limit,
            long publishLeaseSeconds
    ) {
        validateLimit(limit);
        validatePublishLeaseSeconds(publishLeaseSeconds);

        List<OutboxEvent> events = findPublishableEventsForUpdate(
                now,
                limit
        );
        LocalDateTime reservedUntil = now.plusSeconds(publishLeaseSeconds);

        events.forEach(event -> event.reserveForPublish(reservedUntil));

        return events;
    }

    @Transactional(readOnly = true)
    public List<OutboxEvent> findFailedEvents() {
        return outboxEventRepository.findAllByStatusOrderByCreatedAtAsc(
                OutboxStatus.FAILED
        );
    }

    @Transactional
    public void markPublished(String eventId) {
        OutboxEvent event = findEvent(eventId);

        event.markPublished(LocalDateTime.now(clock));
    }

    @Transactional
    public void markPublishFailed(
            String eventId,
            String lastError
    ) {
        OutboxEvent event = findEvent(eventId);
        int nextRetryCount = event.getRetryCount() + 1;
        LocalDateTime nextRetryAt = calculateNextRetryAt(nextRetryCount);

        event.markPublishFailed(
                lastError,
                nextRetryAt
        );
    }

    @Transactional
    public void resetFailedEventForRetry(String eventId) {
        OutboxEvent event = findEvent(eventId);

        event.resetForRetry(LocalDateTime.now(clock));
    }

    private OutboxEvent findEvent(String eventId) {
        return outboxEventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INTERNAL_SERVER_ERROR
                ));
    }

    private LocalDateTime calculateNextRetryAt(int nextRetryCount) {
        if (nextRetryCount >= MAX_RETRY_COUNT) {
            return null;
        }

        return LocalDateTime.now(clock)
                .plusSeconds(BASE_RETRY_DELAY_SECONDS * nextRetryCount);
    }

    private void validateLimit(int limit) {
        if (limit < MIN_FETCH_LIMIT) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private void validatePublishLeaseSeconds(long publishLeaseSeconds) {
        if (publishLeaseSeconds < 1) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private String serialize(OrderCompletedOutboxPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.OUTBOX_EVENT_SAVE_FAILED);
        }
    }
}
