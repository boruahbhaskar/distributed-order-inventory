package com.example.orderservice.config;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.example.orderservice.config.exception.InsufficientStockException;
import com.example.orderservice.config.exception.OrderNotFoundException;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/*
Why @RestControllerAdvice :

@ControllerAdvice intercepts exceptions from all controllers globally.
@RestControllerAdvice  = @ControllerAdvice  + @ResponseBody
Every method return value is automatically serialized to JSON.
Without this, you'd need  @ResponseBody on every handler method

*/

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /*
     * Standard Error Response Shape
     * 
     * Why a consistent error structure:
     * Every error from every endpoint looks the same
     * Client write ONE error-handling block, not one per endpoint
     * Industry Standard - follows RFC 7807 ( Problem details for HTTP APIs)
     */

    // private final OrderJpaRepository orderJpaRepository;

    // GlobalExceptionHandler(OrderJpaRepository orderJpaRepository) {
    // this.orderJpaRepository=orderJpaRepository;
    // }

    public record ErrorResponse(
            Instant timestamp,
            int status,
            String error,
            String message,
            String path,
            Map<String, String> validationErrors // null for non-validation errors
    ) {
        // Factory for simple errors ( no validation details)
        static ErrorResponse of(int status, String error,
                String message, String path) {
            return new ErrorResponse(Instant.now(), status, error, message, path, null);
        }

        // Factory for validation errors ( with field details )
        static ErrorResponse ofValidation(String message, String path, Map<String, String> errors) {
            return new ErrorResponse(Instant.now(), 400, "Validation Failed", message, path, errors);
        }

    }

    // --------------- 400 Bad Request - Validation Failures -------------------
    // Triggered by @Valid on controller method parameters
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        /*
         * Why collect all errors , not just the first :
         * Returning only the first error means the client fixes it, resubmits, and hits
         * the next error.
         * N errors = N round trips
         * Return all errors in one response = one round trip to fix all
         * 
         */

        Map<String, String> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Inavlid Value",
                        (first, second) -> first)); // why merge function : if same field has multiple violations ( e.g.
                                                    // @NotNull and @Size both fail), keep first message

        log.warn("Validation failed for {}  {}: {}",
                request.getMethod(), request.getRequestURI(), fieldErrors);

        return ResponseEntity.badRequest().body(ErrorResponse.ofValidation(
                "Request Validation Failed",
                request.getRequestURI(),
                fieldErrors));
    }

    // ── 400 Bad Request — Type Mismatch ─────────────────────────────────────
    // Triggered when UUID path variable is malformed, or enum value unknown
    // e.g., GET /orders/not-a-uuid or PATCH with status "FLYING"
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {

        String message = String.format(
                "Parameter '%s' has invalid value '%s'",
                ex.getName(), ex.getValue());

        return ResponseEntity
                .badRequest()
                .body(ErrorResponse.of(400, "Bad Request", message,
                        request.getRequestURI()));
    }

    // ── 400 Bad Request — Unreadable Request Body ────────────────────────────
    // Triggered when JSON is malformed (missing quote, wrong type etc.)
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(
            org.springframework.http.converter.HttpMessageNotReadableException ex,
            HttpServletRequest request) {

        return ResponseEntity
                .badRequest()
                .body(ErrorResponse.of(400, "Bad Request",
                        "Request body is malformed or missing",
                        request.getRequestURI()));
    }

    // ── 404 Not Found ────────────────────────────────────────────────────────
    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotFound(
            OrderNotFoundException ex,
            HttpServletRequest request) {

        log.info("Order not found: {}", ex.getOrderId());

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(404, "Not Found",
                        ex.getMessage(), request.getRequestURI()));
    }

    // ── 409 Conflict — Invalid State Transition ──────────────────────────────
    // Triggered when PATCH tries DELIVERED → PENDING etc.
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(
            IllegalStateException ex,
            HttpServletRequest request) {

        log.warn("Illegal state: {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(409, "Conflict",
                        ex.getMessage(), request.getRequestURI()));
    }

    // ── 422 Unprocessable Entity — Business Rule Failure ────────────────────
    // Triggered when request is valid but cannot be fulfilled (no stock)
    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientStock(
            InsufficientStockException ex,
            HttpServletRequest request) {

        log.warn("Insufficient stock: product={}, requested={}",
                ex.getProductId(), ex.getRequested());

        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(422, "Unprocessable Entity",
                        ex.getMessage(), request.getRequestURI()));
    }

    // ── 503 Service Unavailable — Circuit Breaker Open ───────────────────────
    // Triggered when Resilience4j circuit is OPEN (Phase 7)
    @ExceptionHandler(io.github.resilience4j.circuitbreaker.CallNotPermittedException.class)
    public ResponseEntity<ErrorResponse> handleCircuitBreakerOpen(
            io.github.resilience4j.circuitbreaker.CallNotPermittedException ex,
            HttpServletRequest request) {

        log.warn("Circuit breaker OPEN — inventory service unavailable");

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of(503, "Service Unavailable",
                        "Inventory service is currently unavailable. Please retry shortly.",
                        request.getRequestURI()));
    }

    // ── 500 Internal Server Error — Catch All ────────────────────────────────
    // WHY catch Exception last:
    // Anything not caught by specific handlers reaches here.
    // Log the full stack trace for debugging.
    // Return a safe message — NEVER send internal details to the client.
    // Stack traces reveal your framework versions, package structure,
    // and implementation details — all useful to an attacker.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericError(
            Exception ex,
            HttpServletRequest request) {

        log.error("Unhandled exception at {} {}: {}",
                request.getMethod(), request.getRequestURI(),
                ex.getMessage(), ex);

        return ResponseEntity
                .internalServerError()
                .body(ErrorResponse.of(500, "Internal Server Error",
                        "An unexpected error occurred. Please contact support.",
                        request.getRequestURI()));
    }

}
