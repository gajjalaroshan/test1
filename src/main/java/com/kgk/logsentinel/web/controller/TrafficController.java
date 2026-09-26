package com.kgk.logsentinel.web.controller;

import com.kgk.logsentinel.service.logging.ApiRequestContext;
import com.kgk.logsentinel.service.logging.StructuredApiErrorLogger;
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

    @PostMapping("/demo-flagging")
    public ResponseEntity<Map<String, Object>> demoFlagging() {
        List<String> steps = new ArrayList<>();

        for (String scenario : BURST_SCENARIOS) {
            try {
                simulator.simulate(scenario, 50_000);
            } catch (Throwable t) {
                errorLogger.logFailure(
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
            errorLogger.logFailure(
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
