package com.sportify.core.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String DUPLICATE_BOOKING_MESSAGE = "Member already has a confirmed booking for this session";
    private static final String DUPLICATE_REFUND_MESSAGE = "This payment already has a refund; a payment can be refunded only once";
    /** Same value as PaymentRefundService.CODE_PAYMENT_ALREADY_REFUNDED; core does not depend on the payment module. */
    private static final String DUPLICATE_REFUND_CODE = "PAYMENT_ALREADY_REFUNDED";

    @ExceptionHandler(FieldValidationException.class)
    public ResponseEntity<Map<String, Object>> handleFieldValidation(FieldValidationException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), ex.getDetails());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Validation Error", errors);
    }


    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentialsException(BadCredentialsException ex) {
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Invalid email or password", null);
    }

    @ExceptionHandler({DisabledException.class, LockedException.class})
    public ResponseEntity<Map<String, Object>> handleDisabledException(Exception ex) {
        return buildErrorResponse(HttpStatus.FORBIDDEN, "Account is disabled", null);
    }

    @ExceptionHandler(io.jsonwebtoken.JwtException.class)
    public ResponseEntity<Map<String, Object>> handleJwtException(io.jsonwebtoken.JwtException ex) {
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Invalid or expired token", null);
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResourceFoundException(org.springframework.web.servlet.resource.NoResourceFoundException ex) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, "Resource not found", null);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleResourceNotFoundException(ResourceNotFoundException ex) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), null);
    }

    @ExceptionHandler(org.springframework.web.multipart.support.MissingServletRequestPartException.class)
    public ResponseEntity<Map<String, Object>> handleMissingPart(org.springframework.web.multipart.support.MissingServletRequestPartException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Choose a file to upload", null);
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSize(org.springframework.web.multipart.MaxUploadSizeExceededException ex) {
        // Applies to every multipart endpoint, so the message names the configured limit instead of one feature.
        long limit = ex.getMaxUploadSize();
        String message = limit > 0 ? "File must be at most " + formatMegabytes(limit) + " MB" : "File is too large";
        return buildErrorResponse(HttpStatus.PAYLOAD_TOO_LARGE, message, null);
    }

    private static String formatMegabytes(long bytes) {
        double megabytes = bytes / (1024.0 * 1024.0);
        return megabytes == Math.rint(megabytes) ? String.valueOf((long) megabytes) : String.format(Locale.ROOT, "%.1f", megabytes);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessRuleException(BusinessRuleException ex) {
        var response = buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), null);
        if (ex.getCode() != null && response.getBody() != null) {
            response.getBody().put("code", ex.getCode());
        }
        return response;
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, Object>> handleConflictException(ConflictException ex) {
        ResponseEntity<Map<String, Object>> response =
                buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage(), ex.getDetails());
        if (ex.getCode() != null && response.getBody() != null) {
            response.getBody().put("code", ex.getCode());
        }
        return response;
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLockingFailure(ObjectOptimisticLockingFailureException ex) {
        logger.warn("Optimistic locking conflict: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT,
                "The resource was modified by another request. Please reload and try again.", null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        // Log the root cause for operators, but never expose SQL or constraint details to the client.
        String rootMsg = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : "";
        logger.warn("Data integrity violation: {}", rootMsg);
        if (rootMsg != null && rootMsg.toLowerCase(Locale.ROOT).contains("ux_booking_active")) {
            return buildErrorResponse(HttpStatus.CONFLICT, DUPLICATE_BOOKING_MESSAGE, null);
        }
        if (rootMsg != null && rootMsg.toLowerCase(Locale.ROOT).contains("ux_refund_request_payment_live")) {
            ResponseEntity<Map<String, Object>> response = buildErrorResponse(HttpStatus.CONFLICT,
                    DUPLICATE_REFUND_MESSAGE, null);
            if (response.getBody() != null) {
                response.getBody().put("code", DUPLICATE_REFUND_CODE);
            }
            return response;
        }
        return buildErrorResponse(HttpStatus.CONFLICT, "The request conflicts with existing data", null);
    }

    /**
     * Method security (@PreAuthorize) throws inside the controller invocation, so without this handler
     * the catch-all handler below would turn an authorization failure into HTTP 500.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDeniedException(AccessDeniedException ex) {
        return buildErrorResponse(HttpStatus.FORBIDDEN, "Access Denied", null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Malformed request body", null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Invalid value for parameter '" + ex.getName() + "'", null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingRequestParameter(MissingServletRequestParameterException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Required parameter '" + ex.getParameterName() + "' is missing", null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return buildErrorResponse(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGlobalException(Exception ex) {
        logger.error("Unhandled exception: ", ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", null);
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(HttpStatus status, String message, Object details) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("message", message);
        if (details != null) {
            body.put("details", details);
        }
        return new ResponseEntity<>(body, status);
    }
}
