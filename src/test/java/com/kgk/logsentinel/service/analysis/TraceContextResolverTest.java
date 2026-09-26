package com.kgk.logsentinel.service.analysis;

import com.kgk.logsentinel.domain.analysis.LogAnalysisResult;
import com.kgk.logsentinel.domain.analysis.LogEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TraceContextResolverTest {

    private final LogFileAnalyzer analyzer =
            new LogFileAnalyzer(new LogFileParser(), 150_000, 3000, 3);

    @TempDir
    Path tempDir;

    @Test
    void mapsCustomerFromAnyLogLineSharingTraceId() throws Exception {
        Path log = tempDir.resolve("trace.log");
        Files.write(log, List.of(
                "2026-09-25T10:00:00.000Z INFO [exec-1 traceId=trace-abc spanId=span-1] c.k.T - traffic customerId=cust-burst-1 orderId=ord-900",
                "2026-09-25T10:00:01.000Z ERROR [exec-1 traceId=trace-abc spanId=span-1] API_ERROR - API_FAILURE customerId=unknown orderId=unknown amountInr=50000.0 path=/batch exception=java.lang.NullPointerException message=npe",
                "at com.kgk.TrafficSimulator.simulate(TrafficSimulator.java:16)"));

        LogAnalysisResult result = analyzer.analyze(log);

        assertEquals(1, result.errorBreakdown().totalApiFailureErrors());
        assertEquals(
                1,
                result.errorBreakdown().byCustomerId().get("cust-burst-1").totalErrors());
        LogAnalysisResult.CustomerTraceSlice slice =
                result.byCustomer().get("cust-burst-1").byTraceId().get("trace-abc");
        assertEquals(1, slice.errorCount());
        assertEquals(1, slice.errorsByExceptionType().get("NullPointerException"));
    }

    @Test
    void resolveCopiesCustomerOntoErrorEvent() {
        LogEvent info = LogEvent.builder()
                .timestamp(Instant.parse("2026-09-25T10:00:00.000Z"))
                .level("INFO")
                .message("start")
                .traceId("t1")
                .spanId("s1")
                .customerId("cust-a")
                .orderId("ord-a")
                .build();
        LogEvent error = LogEvent.builder()
                .timestamp(Instant.parse("2026-09-25T10:00:01.000Z"))
                .level("ERROR")
                .message("API_FAILURE")
                .traceId("t1")
                .spanId("s1")
                .customerId("unknown")
                .orderId("unknown")
                .build();

        List<LogEvent> resolved = TraceContextResolver.resolve(List.of(info, error));

        assertEquals("cust-a", resolved.get(1).customerId());
        assertEquals("ord-a", resolved.get(1).orderId());
    }
}
