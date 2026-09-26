package com.kgk.logsentinel.domain.analysis;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public record LogEvent(
        Instant timestamp,
        String level,
        String message,
        String traceId,
        String spanId,
        String customerId,
        String orderId,
        Double amountInr,
        String exceptionClass,
        String failureMessage,
        List<String> stackLines,
        boolean explicitCustomerId) {

    /**
     * Distinguishes failures that share the same exception type and top stack frame (e.g. multiple {@code RuntimeException}s).
     */
    public String stackSignature() {
        String frame = stackLines.isEmpty() ? "no-frame" : stackLines.getFirst();
        String detail = failureMessage != null && !failureMessage.isBlank() ? failureMessage : "no-msg";
        return exceptionClass + "|" + frame + "|" + detail;
    }

    public LogEvent withIdentity(String customerId, String orderId) {
        return new LogEvent(
                timestamp,
                level,
                message,
                traceId,
                spanId,
                customerId,
                orderId,
                amountInr,
                exceptionClass,
                failureMessage,
                stackLines,
                explicitCustomerId);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Instant timestamp;
        private String level;
        private String message;
        private String traceId;
        private String spanId;
        private String customerId;
        private String orderId;
        private Double amountInr;
        private String exceptionClass;
        private String failureMessage;
        private boolean explicitCustomerId;
        private final List<String> stackLines = new ArrayList<>();

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder level(String level) {
            this.level = level;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder spanId(String spanId) {
            this.spanId = spanId;
            return this;
        }

        public Builder customerId(String customerId) {
            this.customerId = customerId;
            return this;
        }

        public Builder explicitCustomerId(boolean explicitCustomerId) {
            this.explicitCustomerId = explicitCustomerId;
            return this;
        }

        public Builder orderId(String orderId) {
            this.orderId = orderId;
            return this;
        }

        public Builder amountInr(Double amountInr) {
            this.amountInr = amountInr;
            return this;
        }

        public Builder exceptionClass(String exceptionClass) {
            this.exceptionClass = exceptionClass;
            return this;
        }

        public boolean hasExceptionClass() {
            return exceptionClass != null && !exceptionClass.isBlank();
        }

        public Builder failureMessage(String failureMessage) {
            this.failureMessage = failureMessage;
            return this;
        }

        public Builder addStackLine(String line) {
            this.stackLines.add(line);
            return this;
        }

        public LogEvent build() {
            return new LogEvent(
                    timestamp,
                    level,
                    message,
                    traceId,
                    spanId,
                    customerId,
                    orderId,
                    amountInr,
                    exceptionClass,
                    failureMessage,
                    List.copyOf(stackLines),
                    explicitCustomerId);
        }
    }
}
