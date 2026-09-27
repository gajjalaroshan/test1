package com.kgk.logsentinel.dto;

import java.util.List;
import java.util.Map;

public record LogAnalysisResult(
        String logFilePath,
        int totalEvents,
        int errorEvents,
        ErrorBreakdown errorBreakdown,
        List<StructuredApiError> errors,
        Map<String, CustomerGroup> byCustomer,
        Map<String, OrderGroup> byOrder,
        List<FlaggedOrder> flaggedOrders,
        List<FlaggedCustomer> flaggedCustomers) {

    public record CustomerGroup(
            String customerId,
            int errorCount,
            Map<String, Integer> stackSignatureCounts,
            Map<String, CustomerTraceSlice> byTraceId) {}

    public record CustomerTraceSlice(String traceId, int errorCount, Map<String, Integer> errorsByExceptionType) {}

    public record OrderGroup(String orderId, int errorCount, Double maxAmountInr) {}

    public record FlaggedOrder(String orderId, String customerId, double amountInr, double thresholdInr, String reason) {}

    public record CustomerBurstWindow(int distinctStacksInWindow, String windowStart, String windowEnd) {}

    public record FlaggedCustomer(
            String customerId, long windowMs, String reason, List<CustomerBurstWindow> windows) {}

    /** Full technical payload for Remediation Planner (exception types, traces, stacks). */
    public String toRemediationPrompt() {
        StringBuilder sb = new StringBuilder();
        appendDataSemantics(sb);
        appendRuleFlags(sb);
        appendErrorVolumeTechnical(sb);
        sb.append("Log file: ").append(logFilePath).append('\n');
        sb.append("Events: ").append(totalEvents).append(" parsed log events, ")
                .append(errorEvents)
                .append(" structured API_FAILURE errors\n\n");
        sb.append("Grouped by customerId (customer-level errorCount includes all traces; use byTraceId for per-request scope):\n");
        byCustomer.forEach((id, g) -> {
            sb.append("  - customerId=").append(id).append(" errors=").append(g.errorCount())
                    .append(" stackSignatureCounts=").append(g.stackSignatureCounts()).append('\n');
            g.byTraceId().forEach((traceId, slice) -> sb.append("      traceId=")
                    .append(traceId)
                    .append(" errors=")
                    .append(slice.errorCount())
                    .append(" errorsByExceptionType=")
                    .append(slice.errorsByExceptionType())
                    .append('\n'));
        });
        sb.append("\nGrouped by orderId:\n");
        byOrder.forEach((id, g) -> sb.append("  - orderId=").append(id).append(" errors=").append(g.errorCount())
                .append(" maxAmountInr=").append(g.maxAmountInr()).append('\n'));
        return sb.toString();
    }

    /** Business-safe metrics for Executive Report (no traceId, exception types, or stack data). */
    public String toExecutivePrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== EXECUTIVE METRICS (authoritative — use only these numbers) ===\n");
        sb.append("Parsed log events: ").append(totalEvents).append('\n');
        sb.append("Structured API failure count: ").append(errorEvents).append('\n');
        if (errorBreakdown != null) {
            sb.append("Total API_FAILURE errors (rollup): ").append(errorBreakdown.totalApiFailureErrors()).append('\n');
            sb.append("Failed operations by customer (totals only):\n");
            errorBreakdown.byCustomerId().forEach((id, slice) -> sb.append("  customerId=")
                    .append(id)
                    .append(" failedOperations=")
                    .append(slice.totalErrors())
                    .append('\n'));
            sb.append("Failed operations by order (totals and value):\n");
            errorBreakdown.byOrderId().forEach((id, slice) -> sb.append("  orderId=")
                    .append(id)
                    .append(" failedOperations=")
                    .append(slice.totalErrors())
                    .append(" maxAmountInr=")
                    .append(slice.maxAmountInr())
                    .append('\n'));
        }
        sb.append("=== ATTENTION REQUIRED (rule-based flags) ===\n");
        if (flaggedCustomers.isEmpty()) {
            sb.append("Flagged customers: none\n");
        } else {
            flaggedCustomers.forEach(f -> f.windows().forEach(w -> sb.append("CUSTOMER_ATTENTION customerId=")
                    .append(f.customerId())
                    .append(" distinctFailurePatternsInWindow=")
                    .append(w.distinctStacksInWindow())
                    .append(" window=")
                    .append(w.windowStart())
                    .append("..")
                    .append(w.windowEnd())
                    .append(" reason=")
                    .append(f.reason())
                    .append('\n')));
        }
        if (flaggedOrders.isEmpty()) {
            sb.append("Flagged orders: none\n");
        } else {
            flaggedOrders.forEach(f -> sb.append("ORDER_ATTENTION orderId=")
                    .append(f.orderId())
                    .append(" customerId=")
                    .append(f.customerId())
                    .append(" amountInr=")
                    .append(f.amountInr())
                    .append(" thresholdInr=")
                    .append(f.thresholdInr())
                    .append(" reason=")
                    .append(f.reason())
                    .append('\n'));
        }
        sb.append("=== END EXECUTIVE METRICS ===\n");
        return sb.toString();
    }

    /** Same as {@link #toRemediationPrompt()} — kept for callers expecting the legacy name. */
    public String toAgentPrompt() {
        return toRemediationPrompt();
    }

    private void appendDataSemantics(StringBuilder sb) {
        sb.append("=== DATA SEMANTICS ===\n");
        sb.append("- customerId-level errorCount and ERROR SPLITS byCustomerId: all failures attributed to that customer.\n");
        sb.append("- traceId under byTraceId: failures scoped to one request/trace; sum of trace errors may be less than customer errorCount.\n");
        sb.append("- stackSignatureCounts: distinct stack-trace fingerprints per customer (not a failure count).\n");
        sb.append("- FLAGGED_CUSTOMER: burst of distinct stack signatures in a time window (instability signal).\n");
        sb.append("- FLAGGED_ORDER: order amount exceeded configured threshold during failures.\n");
        sb.append("=== END DATA SEMANTICS ===\n\n");
    }

    private void appendRuleFlags(StringBuilder sb) {
        sb.append("=== DETERMINISTIC RULE FLAGS (cite each line once in playbook — do not duplicate this block) ===\n");
        if (flaggedCustomers.isEmpty()) {
            sb.append("Flagged customers: none\n");
        } else {
            flaggedCustomers.forEach(f -> f.windows().forEach(w -> sb.append("FLAGGED_CUSTOMER customerId=")
                    .append(f.customerId())
                    .append(" distinctStacks=")
                    .append(w.distinctStacksInWindow())
                    .append(" window=")
                    .append(w.windowStart())
                    .append("..")
                    .append(w.windowEnd())
                    .append(" reason=")
                    .append(f.reason())
                    .append('\n')));
        }
        if (flaggedOrders.isEmpty()) {
            sb.append("Flagged orders: none\n");
        } else {
            flaggedOrders.forEach(f -> sb.append("FLAGGED_ORDER orderId=")
                    .append(f.orderId())
                    .append(" customerId=")
                    .append(f.customerId())
                    .append(" amountInr=")
                    .append(f.amountInr())
                    .append(" reason=")
                    .append(f.reason())
                    .append('\n'));
        }
        sb.append("=== END RULE FLAGS ===\n\n");
    }

    private void appendErrorVolumeTechnical(StringBuilder sb) {
        sb.append("=== ERROR VOLUME & SPLITS (authoritative counts — reproduce exactly in evidence) ===\n");
        if (errorBreakdown != null) {
            sb.append("Total API_FAILURE errors: ").append(errorBreakdown.totalApiFailureErrors()).append('\n');
            sb.append("Global split by exception/log type:\n");
            errorBreakdown.errorsByExceptionType().forEach((type, count) -> sb.append("  ")
                    .append(type)
                    .append('=')
                    .append(count)
                    .append('\n'));
            sb.append("Per customerId (total + split by exception type):\n");
            errorBreakdown.byCustomerId().forEach((id, slice) -> sb.append("  customerId=")
                    .append(id)
                    .append(" total=")
                    .append(slice.totalErrors())
                    .append(" byType=")
                    .append(slice.errorsByExceptionType())
                    .append('\n'));
            sb.append("Per orderId (total + split by type + maxAmountInr):\n");
            errorBreakdown.byOrderId().forEach((id, slice) -> sb.append("  orderId=")
                    .append(id)
                    .append(" total=")
                    .append(slice.totalErrors())
                    .append(" byType=")
                    .append(slice.errorsByExceptionType())
                    .append(" maxAmountInr=")
                    .append(slice.maxAmountInr())
                    .append('\n'));
        }
        sb.append("=== END ERROR SPLITS ===\n\n");
    }
}
