package com.example.coffeeorder.common.util;

import java.util.Map;

import com.example.coffeeorder.common.exception.BusinessException;
import com.example.coffeeorder.common.exception.ErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

public final class PageRequestFactory {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    private PageRequestFactory() {
    }

    public static PageRequest create(
            String page,
            String size,
            String sort,
            String defaultSort,
            Map<String, String> sortProperties
    ) {
        return PageRequest.of(
                parsePage(page),
                parseSize(size),
                parseSort(
                        sort,
                        defaultSort,
                        sortProperties
                )
        );
    }

    private static int parsePage(String page) {
        int parsedPage = parseInteger(
                page,
                DEFAULT_PAGE
        );

        if (parsedPage < 0) {
            throw new BusinessException(ErrorCode.INVALID_PAGE_REQUEST);
        }

        return parsedPage;
    }

    private static int parseSize(String size) {
        int parsedSize = parseInteger(
                size,
                DEFAULT_SIZE
        );

        if (parsedSize <= 0) {
            throw new BusinessException(ErrorCode.INVALID_PAGE_REQUEST);
        }

        return parsedSize;
    }

    private static int parseInteger(
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

    private static Sort parseSort(
            String sort,
            String defaultSort,
            Map<String, String> sortProperties
    ) {
        String sortExpression =
                sort == null || sort.isBlank() ? defaultSort : sort.trim();
        String[] parts = sortExpression.split(",");

        if (parts.length > 2 || parts[0].isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PAGE_REQUEST);
        }

        String property = sortProperties.get(parts[0].trim());

        if (property == null) {
            throw new BusinessException(ErrorCode.INVALID_PAGE_REQUEST);
        }

        return Sort.by(
                parseDirection(parts),
                property
        );
    }

    private static Sort.Direction parseDirection(String[] parts) {
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
