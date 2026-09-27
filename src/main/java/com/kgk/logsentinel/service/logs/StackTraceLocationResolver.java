package com.kgk.logsentinel.service.logs;

import com.kgk.logsentinel.dto.ErrorLocation;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StackTraceLocationResolver {

    public static final String APPLICATION_PREFIX = "com.kgk.logsentinel";

    private static final Pattern STACK_FRAME =
            Pattern.compile("^at\\s+(.+)\\.([^.()]+)\\(([^:()]*)(?::(\\d+))?\\)$");

    private StackTraceLocationResolver() {}

    public static ErrorLocation resolve(List<String> stackLines) {
        if (stackLines == null || stackLines.isEmpty()) {
            return null;
        }
        String applicationFrame = null;
        String firstAtFrame = null;
        for (String raw : stackLines) {
            String line = raw == null ? "" : raw.trim();
            if (!line.startsWith("at ")) {
                continue;
            }
            if (firstAtFrame == null) {
                firstAtFrame = line;
            }
            ErrorLocation parsed = parseFrame(line);
            if (parsed != null && parsed.className().startsWith(APPLICATION_PREFIX)) {
                applicationFrame = line;
                break;
            }
        }
        String chosen = applicationFrame != null ? applicationFrame : firstAtFrame;
        return chosen == null ? null : parseFrame(chosen);
    }

    public static ErrorLocation parseFrame(String atLine) {
        Matcher matcher = STACK_FRAME.matcher(atLine.trim());
        if (!matcher.matches()) {
            return null;
        }
        String className = normalizeClassName(matcher.group(1));
        Integer line = matcher.group(4) == null ? null : Integer.parseInt(matcher.group(4));
        return new ErrorLocation(className, line, null);
    }

    private static String normalizeClassName(String raw) {
        int slash = raw.indexOf('/');
        if (slash >= 0) {
            return raw.substring(slash + 1);
        }
        return raw;
    }
}
