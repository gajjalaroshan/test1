package com.kgk.logsentinel.service.analysis;

import com.kgk.logsentinel.domain.analysis.LogAnalysisResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogFileAnalyzerTest {

    private final LogFileAnalyzer analyzer =
            new LogFileAnalyzer(new LogFileParser(), 150_000, 3000, 3);

    @TempDir
    Path tempDir;

    @Test
    void flagsHighValueAndCustomerBurst() throws Exception {
        Path log = tempDir.resolve("test.log");
        Files.write(log, sampleLines());

        LogAnalysisResult result = analyzer.analyze(log);

        assertTrue(result.flaggedOrders().stream().anyMatch(f -> f.amountInr() > 150_000));
        assertTrue(result.flaggedCustomers().stream().anyMatch(f -> "cust-burst-1".equals(f.customerId())));
        assertFalse(result.byCustomer().isEmpty());
        assertEquals(4, result.errorBreakdown().totalApiFailureErrors());
        assertTrue(result.errorBreakdown().byCustomerId().containsKey("cust-burst-1"));
        assertEquals(4, result.errorBreakdown().byCustomerId().get("cust-burst-1").totalErrors());
    }

    private static List<String> sampleLines() {
        return List.of(
                "2026-09-25T10:00:01.000Z ERROR [exec-1] API_ERROR - API_FAILURE customerId=cust-burst-1 orderId=ord-1 amountInr=50000.0 path=/api/v1/traffic/simulate exception=java.lang.NullPointerException message=x",
                "java.lang.NullPointerException: x",
                "at com.kgk.logsentinel.simulation.TrafficSimulator.simulate(TrafficSimulator.java:20)",
                "2026-09-25T10:00:01.200Z ERROR [exec-1] API_ERROR - API_FAILURE customerId=cust-burst-1 orderId=ord-1 amountInr=50000.0 path=/api/v1/traffic/simulate exception=java.lang.IllegalStateException message=y",
                "java.lang.IllegalStateException: y",
                "at com.kgk.logsentinel.simulation.TrafficSimulator.simulate(TrafficSimulator.java:22)",
                "2026-09-25T10:00:01.400Z ERROR [exec-1] API_ERROR - API_FAILURE customerId=cust-burst-1 orderId=ord-1 amountInr=50000.0 path=/api/v1/traffic/simulate exception=java.net.SocketTimeoutException message=z",
                "java.net.SocketTimeoutException: z",
                "at com.kgk.logsentinel.simulation.TrafficSimulator.simulate(TrafficSimulator.java:24)",
                "2026-09-25T10:00:01.600Z ERROR [exec-1] API_ERROR - API_FAILURE customerId=cust-burst-1 orderId=ord-1 amountInr=200000.0 path=/api/v1/traffic/simulate exception=java.lang.RuntimeException message=r",
                "java.lang.RuntimeException: r",
                "at com.kgk.logsentinel.simulation.TrafficSimulator.simulate(TrafficSimulator.java:28)");
    }
}
