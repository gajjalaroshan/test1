package com.kgk.logsentinel.domain.analysis;

import java.util.Map;

/**
 * Deterministic roll-up of API_FAILURE errors: totals and splits Java owns (agents must not invent counts).
 */
public record ErrorBreakdown(
        int totalApiFailureErrors,
        Map<String, Integer> errorsByExceptionType,
        Map<String, CustomerErrorSlice> byCustomerId,
        Map<String, OrderErrorSlice> byOrderId) {

    public record CustomerErrorSlice(int totalErrors, Map<String, Integer> errorsByExceptionType) {}

    public record OrderErrorSlice(int totalErrors, Map<String, Integer> errorsByExceptionType, double maxAmountInr) {}
}
