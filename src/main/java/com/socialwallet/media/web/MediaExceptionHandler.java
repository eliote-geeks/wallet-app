package com.socialwallet.media.web;

import com.socialwallet.media.exception.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.util.Map;

/**
 * Exception handler for the Media module.
 * Maps domain exceptions to appropriate HTTP responses.
 */
@RestControllerAdvice(basePackages = "com.socialwallet.media")
@Slf4j
public class MediaExceptionHandler {

    @ExceptionHandler(MediaNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(MediaNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(InvalidMediaException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidMedia(InvalidMediaException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MediaAccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(MediaAccessDeniedException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(MediaUploadException.class)
    public ResponseEntity<Map<String, Object>> handleUploadError(MediaUploadException ex) {
        log.error("Media upload failed", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Media upload failed. Please try again.");
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimit(RateLimitExceededException ex) {
        return buildResponse(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "File exceeds the maximum upload size");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message,
                "timestamp", Instant.now().toString()
        ));
    }
}
