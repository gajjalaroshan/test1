package com.kgk.logsentinel.config;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LineCountTriggeringPolicyTest {

    private LineCountTriggeringPolicy policy;
    private PatternLayoutEncoder encoder;

    @BeforeEach
    void setUp() {
        LoggerContext context = new LoggerContext();
        encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern("%msg%n");
        encoder.start();

        policy = new LineCountTriggeringPolicy();
        policy.setContext(context);
        policy.setMaxLinesPerFile(5);
        policy.setEncoder(encoder);
        policy.start();
    }

    @Test
    void triggersAfterEncodedNewlinesNotEvents() {
        File active = new File("active.log");
        assertFalse(policy.isTriggeringEvent(active, eventWithMessage("a\nb\n")));
        assertTrue(policy.isTriggeringEvent(active, eventWithMessage("c\nd\n")));
    }

    @Test
    void seedsLineCountFromExistingFile(@TempDir File dir) throws Exception {
        File existing = dir.toPath().resolve("app.log").toFile();
        Files.writeString(existing.toPath(), "1\n2\n3\n4\n", StandardCharsets.UTF_8);

        LineCountTriggeringPolicy seeded = new LineCountTriggeringPolicy();
        seeded.setContext(new LoggerContext());
        seeded.setMaxLinesPerFile(5);
        seeded.setEncoder(encoder);
        seeded.setInitialLineCount(LineBasedRollingFileAppender.countNewlinesInFile(existing));
        seeded.start();

        assertTrue(seeded.isTriggeringEvent(existing, eventWithMessage("x\n")));
    }

    @Test
    void countNewlinesMatchesPhysicalLines() {
        byte[] bytes = "line1\nline2\n".getBytes(StandardCharsets.UTF_8);
        assertTrue(LineCountTriggeringPolicy.countNewlines(bytes) >= 2);
    }

    private static ILoggingEvent eventWithMessage(String message) {
        LoggingEvent event = new LoggingEvent();
        event.setMessage(message);
        return event;
    }
}
