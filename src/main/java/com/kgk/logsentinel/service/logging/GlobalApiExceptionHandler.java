package com.kgk.logsentinel.service.logging;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalApiExceptionHandler {

    private final StructuredApiErrorLogger errorLogger;

    public GlobalApiExceptionHandler(StructuredApiErrorLogger errorLogger) {
        this.errorLogger = errorLogger;
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<Map<String, String>> handle(Throwable ex, HttpServletRequest request) {
        errorLogger.logFailure(request, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "error", ex.getClass().getSimpleName(),
                        "message", ex.getMessage() != null ? ex.getMessage() : "",
                        "logged", "true"));
    }
}
