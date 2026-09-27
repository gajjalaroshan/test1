package com.kgk.logsentinel.dto;

import java.util.List;
import java.util.Map;

public record LogAnalysisResult(
        String logFilePath,
        int totalEvents,
        int errorEvents,
        ErrorBreakdown errorBreakdown,
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

    public String toAgentPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== DETERMINISTIC RULE FLAGS (must cite customerId/orderId in your report) ===\n");
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
        sb.append("=== ERROR VOLUME & SPLITS (authoritative counts — reproduce exactly in remediation evidence) ===\n");
        if (errorBreakdown != null) {
            sb.append("Total API_FAILURE errors: ").append(errorBreakdown.totalApiFailureErrors()).append('\n');
            sb.append("Global split by exception/log type:\n");
            errorBreakdown.errorsByExceptionType().forEach((type, count) -> sb.append("  ")
                    .append(type)
                    .append('=')
                    .append(count)
                    .append('\n'));
            sb.append("Per customerId (total + split by type):\n");
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
        sb.append("Log file: ").append(logFilePath).append('\n');
        sb.append("Events: ").append(totalEvents).append(" parsed log events, ")
                .append(errorEvents)
                .append(" structured API_FAILURE errors\n\n");
        sb.append("Grouped by customerId:\n");
        byCustomer.forEach((id, g) -> {
            sb.append("  - ").append(id).append(" errors=").append(g.errorCount())
                    .append(" stacks=").append(g.stackSignatureCounts()).append('\n');
            g.byTraceId().forEach((traceId, slice) -> sb.append("      traceId=")
                    .append(traceId)
                    .append(" errors=")
                    .append(slice.errorCount())
                    .append(" byType=")
                    .append(slice.errorsByExceptionType())
                    .append('\n'));
        });
        sb.append("\nGrouped by orderId:\n");
        byOrder.forEach((id, g) -> sb.append("  - ").append(id).append(" errors=").append(g.errorCount())
                .append(" maxAmountInr=").append(g.maxAmountInr()).append('\n'));
        sb.append("\nFlagged orders (amount > threshold):\n");
        flaggedOrders.forEach(f -> sb.append("  - orderId=").append(f.orderId())
                .append(" amount=").append(f.amountInr()).append(" — ").append(f.reason()).append('\n'));
        sb.append("\nFlagged customers (distinct stack traces > threshold in ").append(
                        flaggedCustomers.isEmpty() ? "3s" : flaggedCustomers.getFirst().windowMs() + "ms")
                .append("):\n");
        flaggedCustomers.forEach(f -> f.windows().forEach(w -> sb.append("  - customerId=")
                .append(f.customerId())
                .append(" distinctStacks=")
                .append(w.distinctStacksInWindow())
                .append(" window=")
                .append(w.windowStart())
                .append("..")
                .append(w.windowEnd())
                .append(" — ")
                .append(f.reason())
                .append('\n')));
        return sb.toString();
    }
}
