package com.example.coffeeorder.event.entity;

import java.time.LocalDateTime;

import com.example.coffeeorder.common.entity.BaseEntity;
import com.example.coffeeorder.event.client.ExternalOrderEventSendResult;
import com.example.coffeeorder.event.dto.OrderCompletedEventRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "external_order_event_logs",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_external_order_event_logs_event_id",
                columnNames = "event_id"
        )
)
public class ExternalOrderEventLog extends BaseEntity {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;
    private static final int FIRST_ATTEMPT_COUNT = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "event_id",
            nullable = false,
            length = 36
    )
    private String eventId;

    @Column(
            name = "event_type",
            nullable = false,
            length = 50
    )
    private String eventType;

    @Column(
            name = "order_id",
            nullable = false
    )
    private Long orderId;

    @Column(
            name = "member_id",
            nullable = false
    )
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 20
    )
    private ExternalOrderEventStatus status;

    @Column(name = "response_status_code")
    private Integer responseStatusCode;

    @Column(
            name = "error_code",
            length = 60
    )
    private String errorCode;

    @Column(
            name = "error_message",
            length = MAX_ERROR_MESSAGE_LENGTH
    )
    private String errorMessage;

    @Column(
            name = "attempt_count",
            nullable = false
    )
    private int attemptCount;

    @Column(
            name = "sent_at",
            nullable = false
    )
    private LocalDateTime sentAt;

    protected ExternalOrderEventLog() {
    }

    private ExternalOrderEventLog(
            OrderCompletedEventRequest event,
            ExternalOrderEventSendResult result
    ) {
        this.eventId = event.eventId();
        this.eventType = event.eventType();
        this.orderId = event.orderId();
        this.memberId = event.memberId();
        this.status = result.status();
        this.responseStatusCode = result.responseStatusCode();
        this.errorCode = result.errorCode();
        this.errorMessage = truncate(result.errorMessage());
        this.attemptCount = FIRST_ATTEMPT_COUNT;
        this.sentAt = result.sentAt();
    }

    public static ExternalOrderEventLog of(
            OrderCompletedEventRequest event,
            ExternalOrderEventSendResult result
    ) {
        return new ExternalOrderEventLog(
                event,
                result
        );
    }

    private String truncate(String value) {
        if (value == null || value.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return value;
        }

        return value.substring(
                0,
                MAX_ERROR_MESSAGE_LENGTH
        );
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public ExternalOrderEventStatus getStatus() {
        return status;
    }

    public Integer getResponseStatusCode() {
        return responseStatusCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }
}
