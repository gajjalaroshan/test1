package com.kgk.logsentinel.service.logs;

import com.kgk.logsentinel.dto.ErrorBreakdown;
import com.kgk.logsentinel.dto.LogAnalysisResult;
import com.kgk.logsentinel.dto.LogEvent;
import com.kgk.logsentinel.dto.StructuredApiError;
import com.kgk.logsentinel.service.mdc.TraceContextResolver;
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
        return analyze(List.of(logFile));
    }

    public LogAnalysisResult analyze(List<Path> logFiles) throws IOException {
        if (logFiles.isEmpty()) {
            throw new IllegalArgumentException("At least one log file path is required");
        }
        List<Path> ordered = orderForAnalysis(logFiles);
        List<String> lines = new ArrayList<>();
        for (Path file : ordered) {
            lines.addAll(Files.readAllLines(file));
        }
        String sourceLabel = ordered.size() == 1
                ? ordered.getFirst().toString()
                : ordered.stream().map(Path::toString).reduce((a, b) -> a + ";" + b).orElse("");

        List<LogEvent> events = TraceContextResolver.resolve(parser.parse(lines));
        List<LogEvent> errors = events.stream().filter(this::isStructuredApiFailure).toList();

        Map<String, List<LogEvent>> errorsByTrace = groupErrorsByTrace(errors);

        Map<String, LogAnalysisResult.CustomerGroup> byCustomer = new TreeMap<>();
        Map<String, LogAnalysisResult.OrderGroup> byOrder = new TreeMap<>();

        for (LogEvent e : errors) {
            String orderId = nullToUnknown(e.orderId());
            byOrder.compute(orderId, (k, g) -> mergeOrder(g, orderId, e));
        }

        for (LogEvent e : errors) {
            if (!e.explicitCustomerId()) {
                continue;
            }
            String customerId = nullToUnknown(e.customerId());
            byCustomer.compute(customerId, (k, g) -> mergeCustomerExplicit(g, customerId, e, errorsByTrace));
        }

        List<LogAnalysisResult.FlaggedOrder> flaggedOrders = flagHighValueOrders(errors);
        List<LogAnalysisResult.FlaggedCustomer> flaggedCustomers = flagCustomerStackBursts(errors);
        ErrorBreakdown breakdown = buildErrorBreakdown(errors);
        List<StructuredApiError> structuredErrors = errors.stream().map(LogFileAnalyzer::toStructuredApiError).toList();

        return new LogAnalysisResult(
                sourceLabel,
                events.size(),
                errors.size(),
                breakdown,
                structuredErrors,
                byCustomer,
                byOrder,
                flaggedOrders,
                flaggedCustomers);
    }

    private static StructuredApiError toStructuredApiError(LogEvent e) {
        return new StructuredApiError(
                nullToUnknown(e.customerId()),
                nullToUnknown(e.orderId()),
                e.exceptionClass(),
                e.errorLocation());
    }

    private List<Path> orderForAnalysis(List<Path> logFiles) {
        if (logFiles.size() <= 1) {
            return logFiles;
        }
        String detectedBase = logFiles.getFirst().getFileName().toString();
        for (Path path : logFiles) {
            String name = path.getFileName().toString();
            if (name.endsWith(".log") && !name.contains(".log.")) {
                detectedBase = name;
                break;
            }
        }
        final String baseName = detectedBase;
        List<Path> copy = new ArrayList<>(logFiles);
        copy.sort(Comparator.comparingInt((Path p) -> LogDirectoryService.rollIndex(p, baseName)).reversed());
        return copy;
    }

    private static Map<String, List<LogEvent>> groupErrorsByTrace(List<LogEvent> errors) {
        Map<String, List<LogEvent>> byTrace = new HashMap<>();
        for (LogEvent e : errors) {
            String traceId = e.traceId();
            if (traceId == null || traceId.isBlank()) {
                continue;
            }
            byTrace.computeIfAbsent(traceId, k -> new ArrayList<>()).add(e);
        }
        return byTrace;
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

            if (e.explicitCustomerId()) {
                String customerId = nullToUnknown(e.customerId());
                customerTotals.merge(customerId, 1, Integer::sum);
                customerTypes.computeIfAbsent(customerId, k -> new TreeMap<>()).merge(type, 1, Integer::sum);
            }

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

    private static LogAnalysisResult.CustomerGroup mergeCustomerExplicit(
            LogAnalysisResult.CustomerGroup existing,
            String customerId,
            LogEvent explicitEvent,
            Map<String, List<LogEvent>> errorsByTrace) {
        Map<String, Integer> stacks = existing == null
                ? new HashMap<>()
                : new HashMap<>(existing.stackSignatureCounts());
        Map<String, LogAnalysisResult.CustomerTraceSlice> byTrace = existing == null
                ? new TreeMap<>()
                : new TreeMap<>(existing.byTraceId());

        stacks.merge(explicitEvent.stackSignature(), 1, Integer::sum);

        String traceId = explicitEvent.traceId();
        if (traceId != null && !traceId.isBlank() && !byTrace.containsKey(traceId)) {
            List<LogEvent> traceErrors = errorsByTrace.getOrDefault(traceId, List.of(explicitEvent));
            Map<String, Integer> traceTypes = new TreeMap<>();
            for (LogEvent te : traceErrors) {
                traceTypes.merge(exceptionTypeLabel(te), 1, Integer::sum);
            }
            byTrace.put(traceId, new LogAnalysisResult.CustomerTraceSlice(traceId, traceErrors.size(), traceTypes));
        } else if (traceId == null || traceId.isBlank()) {
            String unknownTrace = "unknown";
            LogAnalysisResult.CustomerTraceSlice traceSlice = byTrace.get(unknownTrace);
            Map<String, Integer> traceTypes = traceSlice == null
                    ? new TreeMap<>()
                    : new TreeMap<>(traceSlice.errorsByExceptionType());
            String type = exceptionTypeLabel(explicitEvent);
            traceTypes.merge(type, 1, Integer::sum);
            int traceCount = (traceSlice == null ? 0 : traceSlice.errorCount()) + 1;
            byTrace.put(unknownTrace, new LogAnalysisResult.CustomerTraceSlice(unknownTrace, traceCount, traceTypes));
        }

        int explicitCount = (existing == null ? 0 : existing.errorCount()) + 1;
        return new LogAnalysisResult.CustomerGroup(customerId, explicitCount, stacks, byTrace);
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
            if (!e.explicitCustomerId()) {
                continue;
            }
            byCustomer.computeIfAbsent(nullToUnknown(e.customerId()), k -> new ArrayList<>()).add(e);
        }

        String burstReason = "More than " + customerDistinctStackThreshold
                + " distinct stack signatures for customer in "
                + customerBurstWindowMs
                + "ms";
        List<LogAnalysisResult.FlaggedCustomer> flags = new ArrayList<>();
        for (var entry : byCustomer.entrySet()) {
            List<LogEvent> list = entry.getValue();
            list.sort(Comparator.comparing(LogEvent::timestamp));
            List<LogAnalysisResult.CustomerBurstWindow> windows = new ArrayList<>();
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
                    windows.add(new LogAnalysisResult.CustomerBurstWindow(
                            distinct.size(), start.toString(), end.toString()));
                }
                i = Math.max(i + 1, j);
            }
            if (!windows.isEmpty()) {
                flags.add(new LogAnalysisResult.FlaggedCustomer(
                        entry.getKey(), customerBurstWindowMs, burstReason, windows));
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
