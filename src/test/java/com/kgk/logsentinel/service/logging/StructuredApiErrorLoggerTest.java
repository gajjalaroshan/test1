package com.kgk.logsentinel.service.logging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuredApiErrorLoggerTest {

    @Test
    void omitsUnknownCustomerAndOrderFromMessage() {
        String msg = StructuredApiErrorLogger.buildMessage(
                null,
                "unknown",
                "0",
                "/api/v1/test",
                new RuntimeException("root"),
                new RuntimeException("fail"));
        assertTrue(msg.startsWith("API_FAILURE "));
        assertFalse(msg.contains("customerId="));
        assertFalse(msg.contains("orderId="));
        assertTrue(msg.contains("amountInr=0"));
    }

    @Test
    void includesKnownCustomerAndOrder() {
        String msg = StructuredApiErrorLogger.buildMessage(
                "cust-1",
                "ord-1",
                "100",
                "/api",
                new IllegalStateException(),
                new IllegalStateException("x"));
        assertTrue(msg.contains("customerId=cust-1"));
        assertTrue(msg.contains("orderId=ord-1"));
    }
}
