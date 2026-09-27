package com.kgk.logsentinel.service.logs;

import com.kgk.logsentinel.dto.ApiRequestContext;
import com.kgk.logsentinel.dto.TraceMdc;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Single structured ERROR line per API failure so file analysis can run without bespoke log statements in business code.
 */
@Component
public class StructuredApiErrorLogger {

    private static final Logger log = LoggerFactory.getLogger("API_ERROR");

    public void logFailure(HttpServletRequest request, Throwable ex) {
        String customerId = attr(request, ApiRequestContext.CUSTOMER_ID);
        String orderId = attr(request, ApiRequestContext.ORDER_ID);
        String amount = attr(request, ApiRequestContext.AMOUNT_INR);
        if (amount == null) {
            amount = "0";
        }
        String path = request != null ? request.getRequestURI() : "unknown";
        logStructured(customerId, orderId, amount, path, ex);
    }

    public void logFailure(String customerId, String orderId, double amountInr, String path, Throwable ex) {
        logFailureWithMdc(null, null, customerId, orderId, amountInr, path, ex);
    }

    public void logFailureWithMdc(
            String traceId,
            String spanId,
            String customerId,
            String orderId,
            double amountInr,
            String path,
            Throwable ex) {
        boolean traceSet = traceId != null && !traceId.isBlank();
        boolean spanSet = spanId != null && !spanId.isBlank();
        if (traceSet) {
            MDC.put(TraceMdc.TRACE_ID, traceId);
        }
        if (spanSet) {
            MDC.put(TraceMdc.SPAN_ID, spanId);
        }
        try {
            logStructured(customerId, orderId, String.valueOf(amountInr), path, ex);
        } finally {
            if (traceSet) {
                MDC.remove(TraceMdc.TRACE_ID);
            }
            if (spanSet) {
                MDC.remove(TraceMdc.SPAN_ID);
            }
        }
    }

    private void logStructured(String customerId, String orderId, String amount, String path, Throwable ex) {
        Throwable root = rootCause(ex);
        String message = buildMessage(customerId, orderId, amount, path, root, ex);
        log.error(message, ex);
    }

    static String buildMessage(
            String customerId,
            String orderId,
            String amount,
            String path,
            Throwable root,
            Throwable ex) {
        StringBuilder sb = new StringBuilder("API_FAILURE");
        if (isKnownId(customerId)) {
            sb.append(" customerId=").append(customerId);
        }
        if (isKnownId(orderId)) {
            sb.append(" orderId=").append(orderId);
        }
        sb.append(" amountInr=").append(amount != null ? amount : "0");
        sb.append(" path=").append(path != null ? path : "unknown");
        sb.append(" exception=").append(root.getClass().getName());
        sb.append(" message=").append(ex.getMessage() != null ? ex.getMessage() : "");
        return sb.toString();
    }

    private static Throwable rootCause(Throwable ex) {
        Throwable current = ex;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static String attr(HttpServletRequest request, String key) {
        if (request == null) {
            return null;
        }
        Object value = request.getAttribute(key);
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return isKnownId(text) ? text : null;
    }

    private static boolean isKnownId(String value) {
        return value != null && !value.isBlank() && !"unknown".equalsIgnoreCase(value);
    }
}
