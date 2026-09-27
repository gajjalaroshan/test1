package com.kgk.logsentinel.dto;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogAnalysisResultPromptTest {

    @Test
    void remediationPromptIncludesTechnicalFieldsAndSemantics() {
        LogAnalysisResult result = sampleResult();
        String prompt = result.toRemediationPrompt();

        assertTrue(prompt.contains("DATA SEMANTICS"));
        assertTrue(prompt.contains("traceId=trace-1"));
        assertTrue(prompt.contains("java.lang.NullPointerException"));
        assertTrue(prompt.contains("stackSignatureCounts"));
        assertFalse(prompt.contains("EXECUTIVE METRICS"));
        assertFalse(prompt.contains("Failed operations by customer"));
    }

    @Test
    void executivePromptOmitsTechnicalFields() {
        LogAnalysisResult result = sampleResult();
        String prompt = result.toExecutivePrompt();

        assertTrue(prompt.contains("EXECUTIVE METRICS"));
        assertTrue(prompt.contains("failedOperations=2"));
        assertTrue(prompt.contains("CUSTOMER_ATTENTION"));
        assertTrue(prompt.contains("ORDER_ATTENTION"));
        assertFalse(prompt.contains("traceId"));
        assertFalse(prompt.contains("NullPointerException"));
        assertFalse(prompt.contains("stackSignature"));
        assertFalse(prompt.contains("FLAGGED_CUSTOMER"));
    }

    @Test
    void toAgentPromptDelegatesToRemediation() {
        LogAnalysisResult result = sampleResult();
        assertTrue(result.toAgentPrompt().contains("DATA SEMANTICS"));
    }

    private static LogAnalysisResult sampleResult() {
        ErrorBreakdown breakdown = new ErrorBreakdown(
                2,
                Map.of("java.lang.NullPointerException", 2),
                Map.of("cust-1", new ErrorBreakdown.CustomerErrorSlice(2, Map.of("java.lang.NullPointerException", 2))),
                Map.of("ord-1", new ErrorBreakdown.OrderErrorSlice(2, Map.of("java.lang.NullPointerException", 2), 200_000)));
        LogAnalysisResult.CustomerGroup customerGroup = new LogAnalysisResult.CustomerGroup(
                "cust-1",
                2,
                Map.of("sig-a", 2),
                Map.of("trace-1", new LogAnalysisResult.CustomerTraceSlice(
                        "trace-1", 2, Map.of("java.lang.NullPointerException", 2))));
        return new LogAnalysisResult(
                "/logs/app.log",
                10,
                2,
                breakdown,
                Map.of("cust-1", customerGroup),
                Map.of("ord-1", new LogAnalysisResult.OrderGroup("ord-1", 2, 200_000.0)),
                List.of(new LogAnalysisResult.FlaggedOrder("ord-1", "cust-1", 200_000, 150_000, "amount above threshold")),
                List.of(new LogAnalysisResult.FlaggedCustomer(
                        "cust-1",
                        3000,
                        "burst of distinct stacks",
                        List.of(new LogAnalysisResult.CustomerBurstWindow(4, "t0", "t1")))));
    }
}
