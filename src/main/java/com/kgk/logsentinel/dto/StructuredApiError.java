package com.kgk.logsentinel.dto;

/**
 * One structured API_FAILURE error for analyze API JSON (deterministic, not LLM output).
 */
public record StructuredApiError(
        String customerId,
        String orderId,
        String exceptionClass,
        ErrorLocation errorLocation) {}
