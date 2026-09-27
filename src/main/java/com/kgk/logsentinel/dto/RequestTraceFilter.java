package com.kgk.logsentinel.dto;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Ensures every request-scoped log line carries traceId and spanId in the MDC.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTraceFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = newTraceId();
        String spanId = newSpanId();
        MDC.put(TraceMdc.TRACE_ID, traceId);
        MDC.put(TraceMdc.SPAN_ID, spanId);
        request.setAttribute(TraceMdc.REQUEST_TRACE_ID, traceId);
        request.setAttribute(TraceMdc.REQUEST_SPAN_ID, spanId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TraceMdc.TRACE_ID);
            MDC.remove(TraceMdc.SPAN_ID);
        }
    }

    public static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public static String newSpanId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
