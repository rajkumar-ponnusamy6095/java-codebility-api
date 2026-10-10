package com.example.codebility_api.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(
            ResponseStatusException ex,
            HttpServletRequest request) {

        HttpStatusCode status = ex.getStatusCode();
        String message = ex.getReason() != null
                ? ex.getReason()
                : reasonPhrase(status);

        if (status.is5xxServerError()) {
            log.error("{} {} -> {}: {}",
                    request.getMethod(), request.getRequestURI(),
                    status.value(), message, ex);
        } else {
            log.warn("{} {} -> {}: {}",
                    request.getMethod(), request.getRequestURI(),
                    status.value(), message);
        }

        return ResponseEntity.status(status).body(ErrorResponse.of(
                status.value(),
                reasonPhrase(status),
                message,
                request.getRequestURI()
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(
                    fieldError.getField(),
                    fieldError.getDefaultMessage()
            );
        }
        ex.getBindingResult().getGlobalErrors().forEach(error ->
                errors.putIfAbsent(
                        error.getObjectName(),
                        error.getDefaultMessage()
                ));

        log.warn("{} {} -> 400: validation failed for fields {}",
                request.getMethod(), request.getRequestURI(),
                errors.keySet());

        HttpStatus status = HttpStatus.BAD_REQUEST;

        return ResponseEntity.status(status).body(new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                "Validation failed",
                request.getRequestURI(),
                errors
        ));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex, HttpServletRequest request) {
        log.warn("{} {} -> 400: {}", request.getMethod(), request.getRequestURI(), ex.getClass().getSimpleName());
        return error(HttpStatus.BAD_REQUEST, "Malformed request", request);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateKey(DuplicateKeyException ex, HttpServletRequest request) {
        log.warn("{} {} -> 409: duplicate key", request.getMethod(), request.getRequestURI());
        return error(HttpStatus.CONFLICT, "Resource already exists", request);
    }

    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ErrorResponse> handleFrameworkError(ErrorResponseException ex, HttpServletRequest request) {
        HttpStatusCode status = ex.getStatusCode();
        log.warn("{} {} -> {}: {}", request.getMethod(), request.getRequestURI(), status.value(),
                ex.getClass().getSimpleName());
        String detail = ex.getBody().getDetail();
        return ResponseEntity.status(status).body(ErrorResponse.of(
                status.value(), reasonPhrase(status), detail != null ? detail : reasonPhrase(status),
                request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("{} {} -> 500: unhandled exception", request.getMethod(), request.getRequestURI(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", request);
    }

    private static ResponseEntity<ErrorResponse> error(
            HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(ErrorResponse.of(
                status.value(), status.getReasonPhrase(), message, request.getRequestURI()));
    }

    private static String reasonPhrase(HttpStatusCode status) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return resolved != null ? resolved.getReasonPhrase() : status.toString();
    }
}
