package com.kgk.logsentinel.service.simulator;

import org.springframework.stereotype.Service;

/**
 * Simulates API failures with <strong>no</strong> explicit log statements — failures are recorded by
 * {@link com.kgk.logsentinel.config.GlobalApiExceptionHandler} / {@link com.kgk.logsentinel.service.logs.StructuredApiErrorLogger}.
 */
@Service
public class TrafficSimulator {

    public void simulate(String scenario, double amountInr) {
        switch (scenario) {
            case "silent-null-pointer" -> {
                String label = null;
                label.toLowerCase();
            }
            case "illegal-state" -> throw new IllegalStateException("Downstream payment adapter unavailable");
            case "timeout" -> throw new RuntimeException(
                    "Read timed out: payment-gateway",
                    new java.net.SocketTimeoutException("Read timed out: payment-gateway"));
            case "high-value-silent" -> {
                if (amountInr > 150_000) {
                    throw new HighValueOrderRejectedException("High-value order rejected by risk gate");
                }
                throw new UnexpectedProcessingException("Unexpected processing error");
            }
            default -> throw new IllegalArgumentException("Unknown scenario: " + scenario);
        }
    }

    static final class HighValueOrderRejectedException extends RuntimeException {
        HighValueOrderRejectedException(String message) {
            super(message);
        }
    }

    static final class UnexpectedProcessingException extends RuntimeException {
        UnexpectedProcessingException(String message) {
            super(message);
        }
    }
}
