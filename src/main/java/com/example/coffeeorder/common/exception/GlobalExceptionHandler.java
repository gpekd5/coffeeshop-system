package com.example.coffeeorder.common.exception;

import com.example.coffeeorder.common.response.ApiResponse;
import com.example.coffeeorder.common.response.ValidationErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException exception
    ) {
        ErrorCode errorCode = exception.getErrorCode();

        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<ValidationErrorResponse>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception
    ) {
        ErrorCode errorCode = ErrorCode.INVALID_INPUT;
        ValidationErrorResponse response =
                ValidationErrorResponse.from(exception.getBindingResult());

        return ResponseEntity.status(errorCode.getStatus())
                .body(
                        ApiResponse.error(
                                errorCode,
                                response
                        )
                );
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<ValidationErrorResponse>> handleBindException(
            BindException exception
    ) {
        ErrorCode errorCode = ErrorCode.INVALID_INPUT;
        ValidationErrorResponse response =
                ValidationErrorResponse.from(exception.getBindingResult());

        return ResponseEntity.status(errorCode.getStatus())
                .body(
                        ApiResponse.error(
                                errorCode,
                                response
                        )
                );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidRequestException() {
        ErrorCode errorCode = ErrorCode.INVALID_INPUT;

        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<ValidationErrorResponse>> handleConstraintViolationException(
            ConstraintViolationException exception
    ) {
        ErrorCode errorCode = ErrorCode.INVALID_INPUT;
        ValidationErrorResponse response =
                ValidationErrorResponse.from(exception.getConstraintViolations());

        return ResponseEntity.status(errorCode.getStatus())
                .body(
                        ApiResponse.error(
                                errorCode,
                                response
                        )
                );
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<ValidationErrorResponse>> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception
    ) {
        ErrorCode errorCode = ErrorCode.INVALID_INPUT;
        ValidationErrorResponse response =
                ValidationErrorResponse.fromParameterValidationResults(
                        exception.getParameterValidationResults()
                );

        return ResponseEntity.status(errorCode.getStatus())
                .body(
                        ApiResponse.error(
                                errorCode,
                                response
                        )
                );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<ValidationErrorResponse>> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException exception
    ) {
        ErrorCode errorCode = ErrorCode.INVALID_INPUT;
        ValidationErrorResponse response = ValidationErrorResponse.of(
                exception.getParameterName(),
                "필수 요청 파라미터입니다."
        );

        return ResponseEntity.status(errorCode.getStatus())
                .body(
                        ApiResponse.error(
                                errorCode,
                                response
                        )
                );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(
            Exception exception
    ) {
        log.error("Unhandled exception", exception);

        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;

        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode));
    }
}
