package com.kgk.logsentinel.config;

import com.kgk.logsentinel.service.logs.StructuredApiErrorLogger;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

@RestControllerAdvice
public class GlobalApiExceptionHandler {

    private final StructuredApiErrorLogger errorLogger;

    public GlobalApiExceptionHandler(StructuredApiErrorLogger errorLogger) {
        this.errorLogger = errorLogger;
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<Map<String, String>> handle(Throwable ex, HttpServletRequest request) {
        String path = request.getRequestURI();
        if (ex instanceof NoResourceFoundException && !path.startsWith("/api/v1/")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Not Found", "message", path));
        }
        if (path.startsWith("/api/v1/")) {
            errorLogger.logFailure(request, ex);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "error", ex.getClass().getSimpleName(),
                        "message", ex.getMessage() != null ? ex.getMessage() : "",
                        "logged", String.valueOf(path.startsWith("/api/v1/"))));
    }
}
