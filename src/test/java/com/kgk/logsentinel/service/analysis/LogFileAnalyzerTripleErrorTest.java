package com.kgk.logsentinel.service.analysis;

import com.kgk.logsentinel.domain.analysis.LogAnalysisResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogFileAnalyzerTripleErrorTest {

    private final LogFileAnalyzer analyzer =
            new LogFileAnalyzer(new LogFileParser(), 150_000, 3000, 3);

    @TempDir
    Path tempDir;

    @Test
    void explicitCustomerCountOneTraceSliceThree() throws Exception {
        Path log = tempDir.resolve("triple.log");
        Files.write(log, tripleErrorFixture());

        LogAnalysisResult result = analyzer.analyze(log);

        assertEquals(3, result.errorBreakdown().totalApiFailureErrors());
        assertEquals(1, result.errorBreakdown().byCustomerId().get("cust-mixed-1").totalErrors());
        LogAnalysisResult.CustomerTraceSlice slice =
                result.byCustomer().get("cust-mixed-1").byTraceId().get("trace-triple");
        assertEquals(3, slice.errorCount());
        assertTrue(slice.errorsByExceptionType().containsKey("NullPointerException"));
        assertTrue(slice.errorsByExceptionType().containsKey("IllegalStateException"));
    }

    private static List<String> tripleErrorFixture() {
        return List.of(
                "2026-09-25T11:00:00.000Z ERROR [exec-1 traceId=trace-triple spanId=span-1] API_ERROR - API_FAILURE customerId=cust-mixed-1 orderId=ord-m1 amountInr=1000.0 path=/triple exception=java.lang.NullPointerException message=npe",
                "at com.kgk.TrafficSimulator.simulate(TrafficSimulator.java:16)",
                "2026-09-25T11:00:00.010Z ERROR [exec-1 traceId=trace-triple spanId=span-1] API_ERROR - API_FAILURE amountInr=1000.0 path=/triple exception=java.lang.IllegalStateException message=downstream",
                "at com.kgk.TrafficSimulator.simulate(TrafficSimulator.java:18)",
                "2026-09-25T11:00:00.020Z ERROR [exec-1 traceId=trace-triple spanId=span-1] API_ERROR - API_FAILURE amountInr=1000.0 path=/triple exception=java.net.SocketTimeoutException message=timeout",
                "at com.kgk.TrafficSimulator.simulate(TrafficSimulator.java:19)");
    }
}
