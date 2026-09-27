package com.kgk.logsentinel.service.mdc;

import com.kgk.logsentinel.dto.LogEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Propagates customerId/orderId from any log line sharing the same traceId.
 */
public final class TraceContextResolver {

    private TraceContextResolver() {}

    public static List<LogEvent> resolve(List<LogEvent> events) {
        Map<String, String> customerByTrace = new HashMap<>();
        Map<String, String> orderByTrace = new HashMap<>();

        for (LogEvent event : events) {
            String traceId = event.traceId();
            if (traceId == null || traceId.isBlank()) {
                continue;
            }
            if (isKnownId(event.customerId())) {
                customerByTrace.put(traceId, event.customerId());
            }
            if (isKnownId(event.orderId())) {
                orderByTrace.put(traceId, event.orderId());
            }
        }

        return events.stream().map(event -> resolveOne(event, customerByTrace, orderByTrace)).toList();
    }

    private static LogEvent resolveOne(
            LogEvent event, Map<String, String> customerByTrace, Map<String, String> orderByTrace) {
        String traceId = event.traceId();
        if (traceId == null || traceId.isBlank()) {
            return event;
        }
        String customerId = event.customerId();
        String orderId = event.orderId();
        String resolvedCustomer = isKnownId(customerId) ? customerId : customerByTrace.get(traceId);
        String resolvedOrder = isKnownId(orderId) ? orderId : orderByTrace.get(traceId);
        if (Objects.equals(resolvedCustomer, customerId) && Objects.equals(resolvedOrder, orderId)) {
            return event;
        }
        return event.withIdentity(resolvedCustomer, resolvedOrder);
    }

    public static boolean isKnownId(String value) {
        return value != null && !value.isBlank() && !"unknown".equalsIgnoreCase(value);
    }
}
