package com.example.coffeeorder.common.response;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

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

    public static ValidationErrorResponse from(
            Set<ConstraintViolation<?>> constraintViolations
    ) {
        List<FieldErrorResponse> errors = constraintViolations.stream()
                .map(ValidationErrorResponse::from)
                .sorted()
                .toList();

        return new ValidationErrorResponse(errors);
    }

    public static ValidationErrorResponse fromParameterValidationResults(
            List<ParameterValidationResult> parameterValidationResults
    ) {
        List<FieldErrorResponse> errors = parameterValidationResults.stream()
                .flatMap(result -> result.getResolvableErrors()
                        .stream()
                        .map(error -> from(
                                result.getMethodParameter(),
                                error
                        ))
                )
                .sorted()
                .toList();

        return new ValidationErrorResponse(errors);
    }

    public static ValidationErrorResponse of(
            String field,
            String message
    ) {
        return new ValidationErrorResponse(
                List.of(new FieldErrorResponse(
                        field,
                        message
                ))
        );
    }

    private static FieldErrorResponse from(
            ConstraintViolation<?> constraintViolation
    ) {
        return new FieldErrorResponse(
                extractLeafPropertyName(constraintViolation),
                constraintViolation.getMessage()
        );
    }

    private static FieldErrorResponse from(
            MethodParameter methodParameter,
            MessageSourceResolvable error
    ) {
        return new FieldErrorResponse(
                extractParameterName(methodParameter),
                error.getDefaultMessage()
        );
    }

    private static String extractLeafPropertyName(
            ConstraintViolation<?> constraintViolation
    ) {
        String propertyName = null;

        for (jakarta.validation.Path.Node node
                : constraintViolation.getPropertyPath()) {
            propertyName = node.getName();
        }

        if (propertyName == null || propertyName.isBlank()) {
            return "parameter";
        }

        return propertyName;
    }

    private static String extractParameterName(MethodParameter methodParameter) {
        RequestParam requestParam =
                methodParameter.getParameterAnnotation(RequestParam.class);

        if (requestParam != null) {
            return annotationName(
                    requestParam.name(),
                    requestParam.value(),
                    methodParameter
            );
        }

        PathVariable pathVariable =
                methodParameter.getParameterAnnotation(PathVariable.class);

        if (pathVariable != null) {
            return annotationName(
                    pathVariable.name(),
                    pathVariable.value(),
                    methodParameter
            );
        }

        RequestHeader requestHeader =
                methodParameter.getParameterAnnotation(RequestHeader.class);

        if (requestHeader != null) {
            return annotationName(
                    requestHeader.name(),
                    requestHeader.value(),
                    methodParameter
            );
        }

        return fallbackParameterName(methodParameter);
    }

    private static String annotationName(
            String name,
            String value,
            MethodParameter methodParameter
    ) {
        if (!name.isBlank()) {
            return name;
        }

        if (!value.isBlank()) {
            return value;
        }

        return fallbackParameterName(methodParameter);
    }

    private static String fallbackParameterName(MethodParameter methodParameter) {
        String parameterName = methodParameter.getParameterName();

        if (parameterName == null || parameterName.isBlank()) {
            return "parameter";
        }

        return parameterName;
    }

    public record FieldErrorResponse(
            String field,
            String message
    ) implements Comparable<FieldErrorResponse> {

        private static FieldErrorResponse from(FieldError fieldError) {
            return new FieldErrorResponse(
                    fieldError.getField(),
                    fieldError.getDefaultMessage()
            );
        }

        @Override
        public int compareTo(FieldErrorResponse other) {
            return Comparator.comparing(FieldErrorResponse::field)
                    .thenComparing(FieldErrorResponse::message)
                    .compare(
                            this,
                            other
                    );
        }
    }
}
