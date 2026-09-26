package com.kgk.logsentinel.service.analysis;

import com.kgk.logsentinel.domain.analysis.ErrorBreakdown;
import com.kgk.logsentinel.domain.analysis.LogAnalysisResult;
import com.kgk.logsentinel.domain.analysis.LogEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Service
public class LogFileAnalyzer {

    private final LogFileParser parser;
    private final double highValueThresholdInr;
    private final long customerBurstWindowMs;
    private final int customerDistinctStackThreshold;

    public LogFileAnalyzer(
            LogFileParser parser,
            @Value("${logsentinel.analysis.high-value-amount-inr:150000}") double highValueThresholdInr,
            @Value("${logsentinel.analysis.customer-burst-window-ms:3000}") long customerBurstWindowMs,
            @Value("${logsentinel.analysis.customer-distinct-stack-threshold:3}") int customerDistinctStackThreshold) {
        this.parser = parser;
        this.highValueThresholdInr = highValueThresholdInr;
        this.customerBurstWindowMs = customerBurstWindowMs;
        this.customerDistinctStackThreshold = customerDistinctStackThreshold;
    }

    public LogAnalysisResult analyze(Path logFile) throws IOException {
        List<String> lines = Files.readAllLines(logFile);
        List<LogEvent> events = TraceContextResolver.resolve(parser.parse(lines));
        List<LogEvent> errors = events.stream().filter(this::isStructuredApiFailure).toList();

        Map<String, LogAnalysisResult.CustomerGroup> byCustomer = new TreeMap<>();
        Map<String, LogAnalysisResult.OrderGroup> byOrder = new TreeMap<>();

        for (LogEvent e : errors) {
            String customerId = nullToUnknown(e.customerId());
            String orderId = nullToUnknown(e.orderId());
            byCustomer.compute(customerId, (k, g) -> mergeCustomer(g, customerId, e));
            byOrder.compute(orderId, (k, g) -> mergeOrder(g, orderId, e));
        }

        List<LogAnalysisResult.FlaggedOrder> flaggedOrders = flagHighValueOrders(errors);
        List<LogAnalysisResult.FlaggedCustomer> flaggedCustomers = flagCustomerStackBursts(errors);
        ErrorBreakdown breakdown = buildErrorBreakdown(errors);

        return new LogAnalysisResult(
                logFile.toString(),
                events.size(),
                errors.size(),
                breakdown,
                byCustomer,
                byOrder,
                flaggedOrders,
                flaggedCustomers);
    }

    private static ErrorBreakdown buildErrorBreakdown(List<LogEvent> errors) {
        Map<String, Integer> globalByType = new TreeMap<>();
        Map<String, Map<String, Integer>> customerTypes = new TreeMap<>();
        Map<String, Integer> customerTotals = new TreeMap<>();
        Map<String, Map<String, Integer>> orderTypes = new TreeMap<>();
        Map<String, Integer> orderTotals = new TreeMap<>();
        Map<String, Double> orderMaxAmount = new TreeMap<>();

        for (LogEvent e : errors) {
            String type = exceptionTypeLabel(e);
            globalByType.merge(type, 1, Integer::sum);

            String customerId = nullToUnknown(e.customerId());
            customerTotals.merge(customerId, 1, Integer::sum);
            customerTypes.computeIfAbsent(customerId, k -> new TreeMap<>()).merge(type, 1, Integer::sum);

            String orderId = nullToUnknown(e.orderId());
            orderTotals.merge(orderId, 1, Integer::sum);
            orderTypes.computeIfAbsent(orderId, k -> new TreeMap<>()).merge(type, 1, Integer::sum);
            double amount = e.amountInr() != null ? e.amountInr() : 0;
            orderMaxAmount.merge(orderId, amount, Math::max);
        }

        Map<String, ErrorBreakdown.CustomerErrorSlice> byCustomer = new TreeMap<>();
        customerTotals.forEach((id, total) -> byCustomer.put(
                id, new ErrorBreakdown.CustomerErrorSlice(total, Map.copyOf(customerTypes.get(id)))));

        Map<String, ErrorBreakdown.OrderErrorSlice> byOrder = new TreeMap<>();
        orderTotals.forEach((id, total) -> byOrder.put(
                id,
                new ErrorBreakdown.OrderErrorSlice(
                        total, Map.copyOf(orderTypes.get(id)), orderMaxAmount.getOrDefault(id, 0.0))));

        return new ErrorBreakdown(errors.size(), globalByType, byCustomer, byOrder);
    }

    private static String exceptionTypeLabel(LogEvent event) {
        String ex = event.exceptionClass();
        if (ex == null || ex.isBlank()) {
            return "unknown";
        }
        int dot = ex.lastIndexOf('.');
        return dot >= 0 ? ex.substring(dot + 1) : ex;
    }

    private static LogAnalysisResult.CustomerGroup mergeCustomer(
            LogAnalysisResult.CustomerGroup existing, String customerId, LogEvent e) {
        Map<String, Integer> stacks = existing == null
                ? new HashMap<>()
                : new HashMap<>(existing.stackSignatureCounts());
        stacks.merge(e.stackSignature(), 1, Integer::sum);
        Map<String, LogAnalysisResult.CustomerTraceSlice> byTrace = existing == null
                ? new TreeMap<>()
                : new TreeMap<>(existing.byTraceId());
        String traceId = nullToUnknown(e.traceId());
        LogAnalysisResult.CustomerTraceSlice traceSlice = byTrace.get(traceId);
        Map<String, Integer> traceTypes = traceSlice == null
                ? new TreeMap<>()
                : new TreeMap<>(traceSlice.errorsByExceptionType());
        String type = exceptionTypeLabel(e);
        traceTypes.merge(type, 1, Integer::sum);
        int traceCount = (traceSlice == null ? 0 : traceSlice.errorCount()) + 1;
        byTrace.put(traceId, new LogAnalysisResult.CustomerTraceSlice(traceId, traceCount, traceTypes));
        int count = (existing == null ? 0 : existing.errorCount()) + 1;
        return new LogAnalysisResult.CustomerGroup(customerId, count, stacks, byTrace);
    }

    private static LogAnalysisResult.OrderGroup mergeOrder(
            LogAnalysisResult.OrderGroup existing, String orderId, LogEvent e) {
        int count = (existing == null ? 0 : existing.errorCount()) + 1;
        double amount = e.amountInr() != null ? e.amountInr() : 0;
        double max = existing == null ? amount : Math.max(existing.maxAmountInr(), amount);
        return new LogAnalysisResult.OrderGroup(orderId, count, max);
    }

    private List<LogAnalysisResult.FlaggedOrder> flagHighValueOrders(List<LogEvent> errors) {
        List<LogAnalysisResult.FlaggedOrder> flags = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (LogEvent e : errors) {
            if (e.amountInr() == null || e.amountInr() <= highValueThresholdInr) {
                continue;
            }
            String key = e.orderId() + "|" + e.amountInr();
            if (!seen.add(key)) {
                continue;
            }
            flags.add(new LogAnalysisResult.FlaggedOrder(
                    nullToUnknown(e.orderId()),
                    nullToUnknown(e.customerId()),
                    e.amountInr(),
                    highValueThresholdInr,
                    "Order amount exceeds " + highValueThresholdInr + " INR on ERROR event"));
        }
        return flags;
    }

    private List<LogAnalysisResult.FlaggedCustomer> flagCustomerStackBursts(List<LogEvent> errors) {
        Map<String, List<LogEvent>> byCustomer = new HashMap<>();
        for (LogEvent e : errors) {
            byCustomer.computeIfAbsent(nullToUnknown(e.customerId()), k -> new ArrayList<>()).add(e);
        }

        List<LogAnalysisResult.FlaggedCustomer> flags = new ArrayList<>();
        for (var entry : byCustomer.entrySet()) {
            List<LogEvent> list = entry.getValue();
            list.sort(Comparator.comparing(LogEvent::timestamp));
            int i = 0;
            while (i < list.size()) {
                Instant start = list.get(i).timestamp();
                Instant end = start.plusMillis(customerBurstWindowMs);
                Set<String> distinct = new HashSet<>();
                int j = i;
                while (j < list.size() && !list.get(j).timestamp().isAfter(end)) {
                    distinct.add(list.get(j).stackSignature());
                    j++;
                }
                if (distinct.size() > customerDistinctStackThreshold) {
                    flags.add(new LogAnalysisResult.FlaggedCustomer(
                            entry.getKey(),
                            distinct.size(),
                            customerBurstWindowMs,
                            start.toString(),
                            end.toString(),
                            "More than " + customerDistinctStackThreshold
                                    + " distinct stack signatures for customer in "
                                    + customerBurstWindowMs
                                    + "ms"));
                }
                i = Math.max(i + 1, j);
            }
        }
        return flags;
    }

    private static String nullToUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private boolean isStructuredApiFailure(LogEvent event) {
        return "ERROR".equals(event.level())
                && event.message() != null
                && event.message().contains("API_FAILURE");
    }
}
