package com.kgk.logsentinel.service.logs;

import com.kgk.logsentinel.dto.LogAnalysisResult;
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
        LogAnalysisResult.FlaggedCustomer burstFlag = result.flaggedCustomers().stream()
                .filter(f -> "cust-burst-1".equals(f.customerId()))
                .findFirst()
                .orElseThrow();
        assertEquals(1, burstFlag.windows().size());
        assertTrue(burstFlag.windows().getFirst().distinctStacksInWindow() > 3);
        assertFalse(result.byCustomer().isEmpty());
        assertEquals(4, result.errorBreakdown().totalApiFailureErrors());
        assertTrue(result.errorBreakdown().byCustomerId().containsKey("cust-burst-1"));
        assertEquals(4, result.errorBreakdown().byCustomerId().get("cust-burst-1").totalErrors());
        assertEquals(4, result.errors().size());
        assertEquals(
                "com.kgk.logsentinel.simulation.TrafficSimulator",
                result.errors().getFirst().errorLocation().className());
        assertEquals(20, result.errors().getFirst().errorLocation().line());
    }

    @Test
    void aggregatesMultipleBurstWindowsIntoOneFlaggedCustomer() throws Exception {
        Path log = tempDir.resolve("two-bursts.log");
        Files.write(log, twoBurstClusters());

        LogAnalysisResult result = analyzer.analyze(log);

        LogAnalysisResult.FlaggedCustomer flag = result.flaggedCustomers().stream()
                .filter(f -> "cust-repeat".equals(f.customerId()))
                .findFirst()
                .orElseThrow();
        assertEquals(1, result.flaggedCustomers().stream()
                .filter(f -> "cust-repeat".equals(f.customerId()))
                .count());
        assertEquals(2, flag.windows().size());
    }

    private static List<String> twoBurstClusters() {
        return List.of(
                "2026-09-25T10:00:00.000Z ERROR [e] API_ERROR - API_FAILURE customerId=cust-repeat orderId=ord-1 amountInr=1.0 path=/p exception=java.lang.NullPointerException message=a",
                "java.lang.NullPointerException: a",
                "at com.kgk.a.A.one(A.java:1)",
                "2026-09-25T10:00:00.100Z ERROR [e] API_ERROR - API_FAILURE customerId=cust-repeat orderId=ord-1 amountInr=1.0 path=/p exception=java.lang.IllegalStateException message=b",
                "java.lang.IllegalStateException: b",
                "at com.kgk.b.B.two(B.java:2)",
                "2026-09-25T10:00:00.200Z ERROR [e] API_ERROR - API_FAILURE customerId=cust-repeat orderId=ord-1 amountInr=1.0 path=/p exception=java.net.SocketTimeoutException message=c",
                "java.net.SocketTimeoutException: c",
                "at com.kgk.c.C.three(C.java:3)",
                "2026-09-25T10:00:00.300Z ERROR [e] API_ERROR - API_FAILURE customerId=cust-repeat orderId=ord-1 amountInr=1.0 path=/p exception=java.lang.RuntimeException message=d",
                "java.lang.RuntimeException: d",
                "at com.kgk.d.D.four(D.java:4)",
                "2026-09-25T10:00:10.000Z ERROR [e] API_ERROR - API_FAILURE customerId=cust-repeat orderId=ord-1 amountInr=1.0 path=/p exception=java.io.IOException message=e",
                "java.io.IOException: e",
                "at com.kgk.e.E.five(E.java:5)",
                "2026-09-25T10:00:10.100Z ERROR [e] API_ERROR - API_FAILURE customerId=cust-repeat orderId=ord-1 amountInr=1.0 path=/p exception=java.lang.ArithmeticException message=f",
                "java.lang.ArithmeticException: f",
                "at com.kgk.f.F.six(F.java:6)",
                "2026-09-25T10:00:10.200Z ERROR [e] API_ERROR - API_FAILURE customerId=cust-repeat orderId=ord-1 amountInr=1.0 path=/p exception=java.lang.IndexOutOfBoundsException message=g",
                "java.lang.IndexOutOfBoundsException: g",
                "at com.kgk.g.G.seven(G.java:7)",
                "2026-09-25T10:00:10.300Z ERROR [e] API_ERROR - API_FAILURE customerId=cust-repeat orderId=ord-1 amountInr=1.0 path=/p exception=java.lang.SecurityException message=h",
                "java.lang.SecurityException: h",
                "at com.kgk.h.H.eight(H.java:8)");
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
