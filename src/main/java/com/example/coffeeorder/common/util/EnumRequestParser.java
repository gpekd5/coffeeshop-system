package com.example.coffeeorder.common.util;

import com.example.coffeeorder.common.exception.BusinessException;
import com.example.coffeeorder.common.exception.ErrorCode;

public final class EnumRequestParser {

    private EnumRequestParser() {
    }

    public static <E extends Enum<E>> E parseRequired(
            String value,
            Class<E> enumType,
            ErrorCode errorCode
    ) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(errorCode);
        }

        try {
            return Enum.valueOf(
                    enumType,
                    value.trim()
            );
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(errorCode);
        }
    }

    public static <E extends Enum<E>> E parseOptional(
            String value,
            Class<E> enumType,
            ErrorCode errorCode
    ) {
        if (value == null) {
            return null;
        }

        return parseRequired(
                value,
                enumType,
                errorCode
        );
    }

    public static <E extends Enum<E>> E parseOptionalOrDefault(
            String value,
            Class<E> enumType,
            E defaultValue,
            ErrorCode errorCode
    ) {
        if (value == null) {
            return defaultValue;
        }

        return parseRequired(
                value,
                enumType,
                errorCode
        );
    }
}
