package com.kgk.logsentinel.service.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeminiRateLimitRetryTest {

    @Test
    void detectsQuotaMessage() {
        var ex = new RuntimeException(
                "429 You exceeded your current quota … retry in 5.410223868s.");
        assertTrue(GeminiRateLimitRetry.isRateLimited(ex));
        assertEquals(5910L, GeminiRateLimitRetry.retryDelayMillis(ex));
    }
}
