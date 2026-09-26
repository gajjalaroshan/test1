package com.kgk.logsentinel.service.logging;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Single structured ERROR line per API failure so file analysis can run without bespoke log statements in business code.
 */
@Component
public class StructuredApiErrorLogger {

    private static final Logger log = LoggerFactory.getLogger("API_ERROR");

    public void logFailure(HttpServletRequest request, Throwable ex) {
        String customerId = attr(request, ApiRequestContext.CUSTOMER_ID, "unknown");
        String orderId = attr(request, ApiRequestContext.ORDER_ID, "unknown");
        String amount = attr(request, ApiRequestContext.AMOUNT_INR, "0");
        String path = request != null ? request.getRequestURI() : "unknown";
        logStructured(customerId, orderId, amount, path, ex);
    }

    public void logFailure(String customerId, String orderId, double amountInr, String path, Throwable ex) {
        logStructured(customerId, orderId, String.valueOf(amountInr), path, ex);
    }

    private void logStructured(String customerId, String orderId, String amount, String path, Throwable ex) {
        Throwable root = rootCause(ex);
        log.error(
                "API_FAILURE customerId={} orderId={} amountInr={} path={} exception={} message={}",
                customerId,
                orderId,
                amount,
                path,
                root.getClass().getName(),
                ex.getMessage() != null ? ex.getMessage() : "",
                ex);
    }

    private static Throwable rootCause(Throwable ex) {
        Throwable current = ex;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static String attr(HttpServletRequest request, String key, String defaultValue) {
        if (request == null) {
            return defaultValue;
        }
        Object value = request.getAttribute(key);
        return value != null ? value.toString() : defaultValue;
    }

}
