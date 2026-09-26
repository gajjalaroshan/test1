package com.kgk.logsentinel.service.analysis;

import com.kgk.logsentinel.domain.analysis.LogAnalysisResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogFileAnalyzerMultiFileTest {

    private final LogFileAnalyzer analyzer =
            new LogFileAnalyzer(new LogFileParser(), 150_000, 3000, 3);

    @TempDir
    Path tempDir;

    @Test
    void analyzesMultipleRolledFilesInOldestFirstOrder() throws Exception {
        Path rolled = tempDir.resolve("app.log.2");
        Path active = tempDir.resolve("app.log");
        Files.write(rolled, List.of(
                "2026-09-25T09:00:00.000Z ERROR [t traceId=old-trace spanId=s1] API_ERROR - API_FAILURE customerId=cust-old amountInr=1.0 path=/p exception=java.lang.RuntimeException message=old",
                "at com.kgk.T.a(T.java:1)"));
        Files.write(active, List.of(
                "2026-09-25T10:00:00.000Z ERROR [t traceId=new-trace spanId=s2] API_ERROR - API_FAILURE customerId=cust-new amountInr=2.0 path=/p exception=java.lang.RuntimeException message=new",
                "at com.kgk.T.b(T.java:2)"));

        LogAnalysisResult result = analyzer.analyze(List.of(active, rolled));

        assertEquals(2, result.errorBreakdown().totalApiFailureErrors());
        assertEquals(1, result.errorBreakdown().byCustomerId().get("cust-old").totalErrors());
        assertEquals(1, result.errorBreakdown().byCustomerId().get("cust-new").totalErrors());
    }
}
