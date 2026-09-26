package com.kgk.logsentinel.service.analysis;

import com.kgk.logsentinel.domain.analysis.LogEvent;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LogFileParser {

    private static final Pattern LINE_START =
            Pattern.compile("^(\\d{4}-\\d{2}-\\d{2}T[\\d:.]+Z)\\s+(\\w+)\\s+\\[[^\\]]+\\]\\s+.+?\\s+-\\s+(.*)$");
    private static final Pattern BRACKET = Pattern.compile("\\[([^\\]]+)\\]");
    private static final Pattern FAILURE_MESSAGE = Pattern.compile("\\bmessage=(.*)$");
    private static final Pattern TRACE = Pattern.compile("\\btraceId=([^\\s]+)");
    private static final Pattern SPAN = Pattern.compile("\\bspanId=([^\\s]+)");
    private static final Pattern CUSTOMER = Pattern.compile("\\bcustomerId=([^\\s]+)");
    private static final Pattern ORDER = Pattern.compile("\\borderId=([^\\s]+)");
    private static final Pattern AMOUNT = Pattern.compile("\\bamountInr=([\\d.]+)");
    private static final Pattern EXCEPTION = Pattern.compile("\\bexception=([\\w.$]+)");
    private static final Pattern EXCEPTION_NAME = Pattern.compile("([\\w.$]*Exception)");
    private static final Pattern ERROR_NAME = Pattern.compile("([\\w.$]*Error)");

    public List<LogEvent> parse(List<String> lines) {
        List<LogEvent> events = new ArrayList<>();
        LogEvent.Builder current = null;

        for (String raw : lines) {
            Matcher head = LINE_START.matcher(raw);
            if (head.matches()) {
                if (current != null) {
                    events.add(current.build());
                }
                current = LogEvent.builder()
                        .timestamp(Instant.parse(head.group(1)))
                        .level(head.group(2))
                        .message(head.group(3));
                enrichFromLine(current, raw, head.group(3));
                continue;
            }
            if (current == null) {
                continue;
            }
            String trimmed = raw.trim();
            if (trimmed.startsWith("at ")) {
                current.addStackLine(trimmed);
            } else if (trimmed.contains("Exception") || trimmed.contains("Error:")) {
                if (!current.hasExceptionClass()) {
                    current.exceptionClass(extractExceptionName(trimmed));
                }
                current.addStackLine(trimmed);
            }
        }
        if (current != null) {
            events.add(current.build());
        }
        return events;
    }

    private static void enrichFromLine(LogEvent.Builder builder, String rawLine, String message) {
        String traceId = traceSpanFromBracket(rawLine, TRACE);
        String spanId = traceSpanFromBracket(rawLine, SPAN);
        if (traceId == null || spanId == null) {
            int separator = rawLine.indexOf(" - ");
            String header = separator >= 0 ? rawLine.substring(0, separator) : rawLine;
            if (traceId == null) {
                traceId = firstMatch(TRACE, header);
            }
            if (spanId == null) {
                spanId = firstMatch(SPAN, header);
            }
        }
        if (traceId != null) {
            builder.traceId(traceId);
        }
        if (spanId != null) {
            builder.spanId(spanId);
        }
        enrichFromMessage(builder, message);
    }

    private static String traceSpanFromBracket(String rawLine, Pattern pattern) {
        Matcher bracket = BRACKET.matcher(rawLine);
        if (!bracket.find()) {
            return null;
        }
        return firstMatch(pattern, bracket.group(1));
    }

    private static void enrichFromMessage(LogEvent.Builder builder, String message) {
        Matcher customer = CUSTOMER.matcher(message);
        if (customer.find()) {
            String value = customer.group(1);
            if (TraceContextResolver.isKnownId(value)) {
                builder.customerId(value);
                builder.explicitCustomerId(true);
            }
        }
        match(ORDER, message, builder::orderId);
        Matcher amount = AMOUNT.matcher(message);
        if (amount.find()) {
            builder.amountInr(Double.parseDouble(amount.group(1)));
        }
        Matcher ex = EXCEPTION.matcher(message);
        if (ex.find()) {
            builder.exceptionClass(ex.group(1));
        } else if (message.contains("Exception")) {
            builder.exceptionClass(extractExceptionName(message));
        }
        Matcher failureMsg = FAILURE_MESSAGE.matcher(message);
        if (failureMsg.find()) {
            builder.failureMessage(failureMsg.group(1).trim());
        }
    }

    private static void match(Pattern pattern, String message, Consumer<String> consumer) {
        String value = firstMatch(pattern, message);
        if (value != null) {
            consumer.accept(value);
        }
    }

    private static String firstMatch(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static String extractExceptionName(String text) {
        Matcher exception = EXCEPTION_NAME.matcher(text);
        if (exception.find()) {
            return exception.group(1);
        }
        Matcher error = ERROR_NAME.matcher(text);
        if (error.find()) {
            return error.group(1);
        }
        return "unknown";
    }
}
