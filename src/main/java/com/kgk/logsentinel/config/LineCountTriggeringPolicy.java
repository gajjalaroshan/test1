package com.kgk.logsentinel.config;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.Encoder;
import ch.qos.logback.core.rolling.TriggeringPolicyBase;

/**
 * Rolls the active log file after a fixed number of physical lines in encoded output (newline bytes).
 */
public class LineCountTriggeringPolicy extends TriggeringPolicyBase<ILoggingEvent> {

    private int maxLinesPerFile = 5_000;
    private long lineCount;
    private Encoder<ILoggingEvent> encoder;

    public void setMaxLinesPerFile(int maxLinesPerFile) {
        this.maxLinesPerFile = maxLinesPerFile;
    }

    void setEncoder(Encoder<ILoggingEvent> encoder) {
        this.encoder = encoder;
    }

    void setInitialLineCount(long initialLineCount) {
        this.lineCount = Math.max(0, initialLineCount);
    }

    @Override
    public void start() {
        if (encoder == null) {
            addWarn("LineCountTriggeringPolicy has no encoder; falling back to one line per log event");
        }
        super.start();
    }

    @Override
    public boolean isTriggeringEvent(java.io.File activeFile, ILoggingEvent event) {
        if (!isStarted()) {
            return false;
        }
        if (encoder != null) {
            lineCount += countNewlines(encoder.encode(event));
        } else {
            lineCount++;
        }
        if (lineCount >= maxLinesPerFile) {
            lineCount = 0;
            return true;
        }
        return false;
    }

    static int countNewlines(byte[] bytes) {
        int count = 0;
        for (byte b : bytes) {
            if (b == '\n') {
                count++;
            }
        }
        return count;
    }
}
