package com.kgk.logsentinel.service.analysis;

import com.kgk.logsentinel.domain.analysis.LogAnalysisResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproduces real log shape: four batch scenarios with two RuntimeException messages collapsed before the fix.
 */
class LogFileAnalyzerProductionLogTest {

    private final LogFileAnalyzer analyzer =
            new LogFileAnalyzer(new LogFileParser(), 150_000, 3000, 3);

    @TempDir
    Path tempDir;

    @Test
    void flagsCustBurstWhenFourDistinctFailuresInBatchWindow() throws Exception {
        Path log = tempDir.resolve("prod.log");
        Files.write(log, batchQuad());

        LogAnalysisResult result = analyzer.analyze(log);

        assertTrue(
                result.flaggedCustomers().stream().anyMatch(f -> "cust-burst-1".equals(f.customerId())),
                "expected cust-burst-1 flagged; stacks="
                        + result.byCustomer().get("cust-burst-1").stackSignatureCounts());
    }

    private static List<String> batchQuad() {
        return List.of(
                "2026-09-25T10:36:05.953Z ERROR [exec-3] API_ERROR - API_FAILURE customerId=cust-burst-1 orderId=ord-900 amountInr=50000.0 path=/batch exception=java.lang.NullPointerException message=npe",
                "\tat com.kgk.TrafficSimulator.simulate(TrafficSimulator.java:16)",
                "2026-09-25T10:36:05.958Z ERROR [exec-3] API_ERROR - API_FAILURE customerId=cust-burst-1 orderId=ord-900 amountInr=50000.0 path=/batch exception=java.lang.IllegalStateException message=downstream",
                "\tat com.kgk.TrafficSimulator.simulate(TrafficSimulator.java:18)",
                "2026-09-25T10:36:05.963Z ERROR [exec-3] API_ERROR - API_FAILURE customerId=cust-burst-1 orderId=ord-900 amountInr=50000.0 path=/batch exception=java.net.SocketTimeoutException message=Read timed out: payment-gateway",
                "\tat com.kgk.TrafficSimulator.simulate(TrafficSimulator.java:19)",
                "2026-09-25T10:36:05.968Z ERROR [exec-3] API_ERROR - API_FAILURE customerId=cust-burst-1 orderId=ord-900 amountInr=50000.0 path=/batch exception=com.kgk.logsentinel.simulation.TrafficSimulator$UnexpectedProcessingException message=Unexpected processing error",
                "\tat com.kgk.TrafficSimulator.simulate(TrafficSimulator.java:26)");
    }
}
