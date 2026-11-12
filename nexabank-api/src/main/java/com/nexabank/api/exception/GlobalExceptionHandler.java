package com.nexabank.api.exception;

import com.nexabank.api.dto.common.ErrorResponse;
import com.nexabank.api.exception.AuthenticationRequiredException;
import com.nexabank.api.exception.DuplicateUserException;
import com.nexabank.api.exception.InvalidConfirmationTokenException;
import com.nexabank.api.exception.InvalidOtpException;
import com.nexabank.api.exception.InvalidWebhookSignatureException;
import com.nexabank.api.exception.PinLockedException;
import com.nexabank.api.exception.InvalidCurrencyException;
import com.nexabank.api.exception.QuoteExpiredException;
import com.nexabank.api.exception.UnsupportedCurrencyException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(AuthenticationRequiredException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationRequired(AuthenticationRequiredException ex, HttpServletRequest request) {
        log.warn("Authentication required: {}", ex.getMessage());
        return buildResponse(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidConfirmationTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidConfirmationToken(InvalidConfirmationTokenException ex, HttpServletRequest request) {
        log.warn("Invalid confirmation token: {}", ex.getMessage());
        return buildResponse(HttpStatus.UNAUTHORIZED, "INVALID_CONFIRMATION_TOKEN", ex.getMessage(), request);
    }

    @ExceptionHandler(PinLockedException.class)
    public ResponseEntity<ErrorResponse> handlePinLocked(PinLockedException ex, HttpServletRequest request) {
        log.warn("PIN locked: {}", ex.getMessage());
        return buildResponse(HttpStatus.LOCKED, "PIN_LOCKED", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidWebhookSignatureException.class)
    public ResponseEntity<ErrorResponse> handleInvalidWebhookSignature(InvalidWebhookSignatureException ex, HttpServletRequest request) {
        log.warn("Invalid webhook signature: {}", ex.getMessage());
        return buildResponse(HttpStatus.UNAUTHORIZED, "INVALID_WEBHOOK_SIGNATURE", ex.getMessage(), request);
    }

    @ExceptionHandler(DuplicateUserException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateUser(DuplicateUserException ex, HttpServletRequest request) {
        log.warn("Duplicate user registration attempt: {}", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, "DUPLICATE_USER", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidOtpException.class)
    public ResponseEntity<ErrorResponse> handleInvalidOtp(InvalidOtpException ex, HttpServletRequest request) {
        log.warn("Invalid OTP: {}", ex.getMessage());
        return buildResponse(HttpStatus.UNAUTHORIZED, "INVALID_OTP", ex.getMessage(), request);
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFunds(InsufficientFundsException ex, HttpServletRequest request) {
        log.warn("Insufficient funds: {}", ex.getMessage());
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidTransactionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTransaction(InvalidTransactionException ex, HttpServletRequest request) {
        log.warn("Invalid transaction: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "INVALID_TRANSACTION", ex.getMessage(), request);
    }

    @ExceptionHandler(UnauthorizedOperationException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedOperationException ex, HttpServletRequest request) {
        log.warn("Unauthorized operation: {}", ex.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied: {}", ex.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this action", request);
    }

    @ExceptionHandler(InvalidCurrencyException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCurrency(InvalidCurrencyException ex, HttpServletRequest request) {
        log.warn("Invalid currency: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "INVALID_CURRENCY", ex.getMessage(), request);
    }

    @ExceptionHandler(UnsupportedCurrencyException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedCurrency(UnsupportedCurrencyException ex, HttpServletRequest request) {
        log.warn("Unsupported currency: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "UNSUPPORTED_CURRENCY", ex.getMessage(), request);
    }

    @ExceptionHandler(QuoteExpiredException.class)
    public ResponseEntity<ErrorResponse> handleQuoteExpired(QuoteExpiredException ex, HttpServletRequest request) {
        log.warn("Quote expired: {}", ex.getMessage());
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, "QUOTE_EXPIRED", ex.getMessage(), request);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingRequestHeader(MissingRequestHeaderException ex, HttpServletRequest request) {
        log.warn("Missing required header: {}", ex.getHeaderName());
        return buildResponse(HttpStatus.BAD_REQUEST, "MISSING_HEADER",
                "Missing required header: " + ex.getHeaderName(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("Validation failed");
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex, HttpServletRequest request) {
        log.error("Internal server error: ", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "Internal server error", request);
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String error, String message, HttpServletRequest request) {
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(error)
                .message(message)
                .path(request != null ? request.getRequestURI() : null)
                .traceId(UUID.randomUUID().toString().substring(0, 8))
                .build();
        return ResponseEntity.status(status).body(response);
    }
}
