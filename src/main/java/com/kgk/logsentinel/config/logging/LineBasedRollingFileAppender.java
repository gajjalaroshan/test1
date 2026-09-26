package com.kgk.logsentinel.config.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.RollingFileAppender;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Wires {@link LineCountTriggeringPolicy} with the file encoder and seeds line count from the active file on startup.
 */
public class LineBasedRollingFileAppender extends RollingFileAppender<ILoggingEvent> {

    @Override
    public void start() {
        if (getTriggeringPolicy() instanceof LineCountTriggeringPolicy linePolicy) {
            linePolicy.setEncoder(getEncoder());
            String path = getFile();
            if (path != null) {
                File active = new File(path);
                if (active.isFile()) {
                    linePolicy.setInitialLineCount(countNewlinesInFile(active));
                }
            }
        }
        super.start();
    }

    static long countNewlinesInFile(File file) {
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            return LineCountTriggeringPolicy.countNewlines(bytes);
        } catch (IOException e) {
            return 0;
        }
    }
}
