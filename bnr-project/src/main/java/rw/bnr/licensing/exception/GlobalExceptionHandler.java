package rw.bnr.licensing.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import rw.bnr.licensing.dto.ApiError;

import java.util.stream.Collectors;

/**
 * GlobalExceptionHandler — catches ALL exceptions thrown anywhere in the app.
 *
 * Key rules enforced here:
 * 1. NO raw stack traces in responses — ever.
 * 2. Unauthorised = 403, not 404 or 500.
 * 3. Human-readable messages for UI display.
 * 4. Full stack trace logged internally for debugging.
 *
 * The user sees: { "status": 403, "error": "Forbidden", "message": "..." }
 * The logs show: full exception with stack trace for debugging.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ── Concurrent modification — optimistic lock conflict ───────────────
    // Two users acted on the same application simultaneously.
    // The second one loses — returns 409 Conflict.
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        log.warn("Optimistic locking conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, "Conflict",
                        "This application was modified by another user. Please refresh and try again."));
    }

    // ── Illegal state transition ─────────────────────────────────────────
    @ExceptionHandler(IllegalStateTransitionException.class)
    public ResponseEntity<ApiError> handleIllegalTransition(IllegalStateTransitionException ex) {
        log.warn("Illegal state transition: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiError.of(400, "Bad Request", ex.getMessage()));
    }

    // ── Four-eyes rule violation / role violation ────────────────────────
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(403, "Forbidden", ex.getMessage()));
    }

    // ── Authentication failure ───────────────────────────────────────────
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(401, "Unauthorized", "Invalid credentials"));
    }

    // ── Resource not found ───────────────────────────────────────────────
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(404, "Not Found", ex.getMessage()));
    }

    // ── Validation errors (@Valid on DTOs) ───────────────────────────────
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("Validation failed: {}", message);
        return ResponseEntity.badRequest()
                .body(ApiError.of(400, "Validation Failed", message));
    }

    // ── File too large ───────────────────────────────────────────────────
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleFileTooLarge(MaxUploadSizeExceededException ex) {
        log.warn("File upload too large: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(400, "Bad Request",
                        "File size exceeds the maximum allowed limit of 5MB"));
    }

    // ── Business logic violations ────────────────────────────────────────
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiError.of(400, "Bad Request", ex.getMessage()));
    }

    // ── Catch-all — unexpected errors ────────────────────────────────────
    // Logs full stack trace internally but returns a generic message externally.
    // The user NEVER sees internal error details.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneral(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex); // full stack trace in logs
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(500, "Internal Server Error",
                        "An unexpected error occurred. Please contact support."));
    }
}
