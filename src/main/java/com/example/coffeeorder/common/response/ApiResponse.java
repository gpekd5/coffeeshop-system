package com.example.coffeeorder.common.response;

import com.example.coffeeorder.common.exception.ErrorCode;

public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data
) {

    private static final String SUCCESS_CODE = "SUCCESS";
    private static final String DEFAULT_SUCCESS_MESSAGE = "요청이 성공했습니다.";

    public static <T> ApiResponse<T> success(
            String message,
            T data
    ) {
        return success(
                SUCCESS_CODE,
                message,
                data
        );
    }

    public static <T> ApiResponse<T> success(
            String code,
            String message,
            T data
    ) {
        return new ApiResponse<>(
                true,
                code,
                message,
                data
        );
    }

    public static <T> ApiResponse<T> success(T data) {
        return success(
                DEFAULT_SUCCESS_MESSAGE,
                data
        );
    }

    public static ApiResponse<Void> error(ErrorCode errorCode) {
        return error(
                errorCode.getCode(),
                errorCode.getMessage()
        );
    }

    public static ApiResponse<Void> error(
            String code,
            String message
    ) {
        return new ApiResponse<>(
                false,
                code,
                message,
                null
        );
    }

    public static <T> ApiResponse<T> error(
            ErrorCode errorCode,
            T data
    ) {
        return new ApiResponse<>(
                false,
                errorCode.getCode(),
                errorCode.getMessage(),
                data
        );
    }
}
