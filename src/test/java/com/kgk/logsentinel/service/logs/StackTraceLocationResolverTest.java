package com.kgk.logsentinel.service.logs;

import com.kgk.logsentinel.dto.ErrorLocation;
import com.kgk.logsentinel.dto.LogEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StackTraceLocationResolverTest {

    @Test
    void prefersApplicationFrameFromProductionLogLine() {
        List<String> stack = List.of(
                "java.lang.NullPointerException: Cannot invoke \"String.toLowerCase()\" because \"label\" is null",
                "at com.kgk.logsentinel.web.controller.TrafficController.simulateSingleTraceTripleError(TrafficController.java:94)",
                "at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(Unknown Source)");

        ErrorLocation location = StackTraceLocationResolver.resolve(stack);

        assertEquals("com.kgk.logsentinel.web.controller.TrafficController", location.className());
        assertEquals(94, location.line());
        assertNull(location.column());
    }

    @Test
    void fallsBackToFirstAtFrameWhenNoApplicationFrame() {
        List<String> stack = List.of(
                "java.net.SocketTimeoutException: timed out",
                "at okhttp3.internal.connection.RealConnection.connect(RealConnection.java:123)");

        ErrorLocation location = StackTraceLocationResolver.resolve(stack);

        assertEquals("okhttp3.internal.connection.RealConnection", location.className());
        assertEquals(123, location.line());
    }

    @Test
    void unknownSourceHasNullLine() {
        ErrorLocation location = StackTraceLocationResolver.parseFrame(
                "at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(Unknown Source)");

        assertEquals("jdk.internal.reflect.DirectMethodHandleAccessor", location.className());
        assertNull(location.line());
    }

    @Test
    void parserBuildResolvesLocationOnEvent() {
        var lines = List.of(
                "2026-09-26T17:12:56.158Z ERROR [http-nio-8080-exec-3 traceId=a8c84ee2239e4f5e spanId=da6d084b] API_ERROR - API_FAILURE customerId=cust-mixed-1 orderId=ord-mixed-1 amountInr=1000.0 path=/api/v1/traffic/simulate-single-trace-triple-error exception=java.lang.NullPointerException message=Cannot invoke",
                "java.lang.NullPointerException: Cannot invoke",
                "\tat com.kgk.logsentinel.web.controller.TrafficController.simulateSingleTraceTripleError(TrafficController.java:94)");

        LogEvent event = new LogFileParser().parse(lines).getFirst();

        assertEquals("com.kgk.logsentinel.web.controller.TrafficController", event.errorLocation().className());
        assertEquals(94, event.errorLocation().line());
    }
}
