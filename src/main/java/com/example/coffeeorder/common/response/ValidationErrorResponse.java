package com.example.coffeeorder.common.response;

import java.util.Comparator;
import java.util.List;

import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

public record ValidationErrorResponse(
        List<FieldErrorResponse> errors
) {

    public static ValidationErrorResponse from(BindingResult bindingResult) {
        List<FieldErrorResponse> errors = bindingResult.getFieldErrors()
                .stream()
                .sorted(
                        Comparator.comparing(FieldError::getField)
                                .thenComparing(FieldError::getDefaultMessage)
                )
                .map(FieldErrorResponse::from)
                .toList();

        return new ValidationErrorResponse(errors);
    }

    public record FieldErrorResponse(
            String field,
            String message
    ) {

        private static FieldErrorResponse from(FieldError fieldError) {
            return new FieldErrorResponse(
                    fieldError.getField(),
                    fieldError.getDefaultMessage()
            );
        }
    }
}
