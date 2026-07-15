package com.example.coffeeorder.event.client;

import java.time.LocalDateTime;

import com.example.coffeeorder.common.exception.ErrorCode;
import com.example.coffeeorder.event.entity.ExternalOrderEventStatus;

public record ExternalOrderEventSendResult(
        ExternalOrderEventStatus status,
        Integer responseStatusCode,
        String errorCode,
        String errorMessage,
        LocalDateTime sentAt
) {

    public static ExternalOrderEventSendResult success(
            int responseStatusCode,
            LocalDateTime sentAt
    ) {
        return new ExternalOrderEventSendResult(
                ExternalOrderEventStatus.SUCCESS,
                responseStatusCode,
                null,
                null,
                sentAt
        );
    }

    public static ExternalOrderEventSendResult failed(
            Integer responseStatusCode,
            String errorMessage,
            LocalDateTime sentAt
    ) {
        ErrorCode errorCode = ErrorCode.EXTERNAL_DATA_COLLECTION_FAILED;

        return new ExternalOrderEventSendResult(
                ExternalOrderEventStatus.FAILED,
                responseStatusCode,
                errorCode.getCode(),
                errorMessage,
                sentAt
        );
    }

    public static ExternalOrderEventSendResult timeout(
            String errorMessage,
            LocalDateTime sentAt
    ) {
        ErrorCode errorCode = ErrorCode.EXTERNAL_DATA_COLLECTION_TIMEOUT;

        return new ExternalOrderEventSendResult(
                ExternalOrderEventStatus.TIMEOUT,
                null,
                errorCode.getCode(),
                errorMessage,
                sentAt
        );
    }
}
