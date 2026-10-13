package com.portfolio.legacyexplainer.api;

import com.portfolio.legacyexplainer.explain.ExplainerUnavailableException;
import com.portfolio.legacyexplainer.source.SourceFetchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage())
                .findFirst()
                .orElse("Invalid request");
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadable(HttpMessageNotReadableException ex) {
        // Surface validation messages thrown while binding (e.g. an unknown mode).
        Throwable cause = ex.getMostSpecificCause();
        String message = cause instanceof IllegalArgumentException && cause.getMessage() != null
                ? cause.getMessage()
                : "Malformed request body";
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    @ExceptionHandler(SourceFetchException.class)
    public ResponseEntity<Map<String, String>> handleSourceFetch(SourceFetchException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ExplainerUnavailableException.class)
    public ResponseEntity<Map<String, String>> handleExplainerUnavailable(ExplainerUnavailableException ex) {
        log.warn("LLM backend unavailable: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneric(Exception ex) {
        log.error("Unexpected error handling request", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Something went wrong while explaining the code. Please try again."));
    }
}
