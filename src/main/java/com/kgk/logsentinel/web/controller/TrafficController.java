package com.kgk.logsentinel.web.controller;

import com.kgk.logsentinel.service.logging.ApiRequestContext;
import com.kgk.logsentinel.service.logging.RequestTraceFilter;
import com.kgk.logsentinel.service.logging.StructuredApiErrorLogger;
import com.kgk.logsentinel.service.logging.TraceMdc;
import com.kgk.logsentinel.service.simulator.TrafficSimulator;
import com.kgk.logsentinel.web.dto.TrafficRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/traffic")
public class TrafficController {

    private final TrafficSimulator simulator;
    private final StructuredApiErrorLogger errorLogger;

    public TrafficController(TrafficSimulator simulator, StructuredApiErrorLogger errorLogger) {
        this.simulator = simulator;
        this.errorLogger = errorLogger;
    }

    public static final String DEMO_CUSTOMER_BURST = "cust-burst-1";
    public static final String DEMO_ORDER_BURST = "ord-900";
    public static final String DEMO_CUSTOMER_VIP = "cust-vip";
    public static final String DEMO_ORDER_VIP = "ord-vip-1";
    public static final double DEMO_VIP_AMOUNT_INR = 200_000;
    public static final String DEMO_CUSTOMER_MIXED = "cust-mixed-1";
    private static final List<String> BURST_SCENARIOS = List.of(
        "silent-null-pointer", "illegal-state", "timeout", "high-value-silent");

    @PostMapping("/simulate-batch")
    public ResponseEntity<Map<String, Object>> simulateBatch(
        @RequestBody TrafficRequest body, HttpServletRequest request) {
        ApiRequestContext.bindTraffic(request, body.customerId(), body.orderId(), body.amountInr());
        List<String> recorded = new ArrayList<>();
        for (String scenario : body.scenarios()) {
            try {
                simulator.simulate(scenario, body.amountInr());
            } catch (Throwable t) {
                errorLogger.logFailure(request, t);
                recorded.add(scenario + " -> " + t.getClass().getSimpleName());
            }
        }
        return ResponseEntity.ok(Map.of(
            "status", "recorded",
            "failuresLogged", recorded.size(),
            "scenarios", recorded,
            "logFileHint", "See logsentinel.log-file (default logs/log-sentinel-app.log)"));
    }

    @PostMapping("/simulate-single-trace-triple-error")
    public ResponseEntity<Map<String, Object>> simulateSingleTraceTripleError(HttpServletRequest request) {
        String traceId = (String) request.getAttribute(TraceMdc.REQUEST_TRACE_ID);
        String spanId = (String) request.getAttribute(TraceMdc.REQUEST_SPAN_ID);
        List<String> steps = new ArrayList<>();

        ApiRequestContext.bindTraffic(request, DEMO_CUSTOMER_MIXED, "ord-mixed-1", 1000);
        try {
            simulator.simulate("silent-null-pointer", 1000);
        } catch (Throwable t) {
            errorLogger.logFailure(request, t);
            steps.add("1-with-customer -> " + t.getClass().getSimpleName());
        }

        request.removeAttribute(ApiRequestContext.CUSTOMER_ID);
        request.removeAttribute(ApiRequestContext.ORDER_ID);
        request.setAttribute(ApiRequestContext.AMOUNT_INR, 1000.0);

        try {
            simulator.simulate("illegal-state", 1000);
        } catch (Throwable t) {
            errorLogger.logFailure(request, t);
            steps.add("2-no-customer -> " + t.getClass().getSimpleName());
        }

        try {
            simulator.simulate("timeout", 1000);
        } catch (Throwable t) {
            errorLogger.logFailure(request, t);
            steps.add("3-no-customer -> " + t.getClass().getSimpleName());
        }

        return ResponseEntity.ok(Map.of(
            "status", "recorded",
            "traceId", traceId != null ? traceId : "",
            "spanId", spanId != null ? spanId : "",
            "steps", steps,
            "expectedAnalysis", Map.of(
                "totalApiFailureErrors", 3,
                "explicitCustomerErrors", 1,
                "customerId", DEMO_CUSTOMER_MIXED,
                "byTraceIdErrorCount", 3,
                "notes", List.of(
                    "Three API_FAILURE lines share one HTTP request traceId",
                    "Only the first line includes customerId; customer totals must not count the other two",
                    "Trace slice for this customer should still show 3 errors on that traceId"))));
    }

    @PostMapping("/demo-flagging")
    public ResponseEntity<Map<String, Object>> demoFlagging() {
        String traceId = RequestTraceFilter.newTraceId();
        String spanId = RequestTraceFilter.newSpanId();
        List<String> steps = new ArrayList<>();

        for (String scenario : BURST_SCENARIOS) {
            try {
                simulator.simulate(scenario, 50_000);
            } catch (Throwable t) {
                errorLogger.logFailureWithMdc(
                    traceId,
                    spanId,
                    DEMO_CUSTOMER_BURST,
                    DEMO_ORDER_BURST,
                    50_000,
                    "/api/v1/traffic/demo-flagging",
                    t);
                steps.add("burst:" + scenario + " -> " + t.getClass().getSimpleName());
            }
        }

        try {
            simulator.simulate("high-value-silent", DEMO_VIP_AMOUNT_INR);
        } catch (Throwable t) {
            errorLogger.logFailureWithMdc(
                traceId,
                spanId,
                DEMO_CUSTOMER_VIP,
                DEMO_ORDER_VIP,
                DEMO_VIP_AMOUNT_INR,
                "/api/v1/traffic/demo-flagging",
                t);
            steps.add("high-value:high-value-silent -> " + t.getClass().getSimpleName());
        }

        return ResponseEntity.ok(Map.of(
            "status", "recorded",
            "steps", steps,
            "expectedRuleFlags", Map.of(
                "flaggedCustomerIds", List.of(DEMO_CUSTOMER_BURST),
                "flaggedOrderIds", List.of(DEMO_ORDER_VIP),
                "notes", List.of(
                    "cust-burst-1: >3 distinct stack traces within 3s",
                    "ord-vip-1: amountInr > 150000 on ERROR")),
            "next", "POST /api/v1/logs/analyze with {\"useLlm\":true}"));
    }
}
