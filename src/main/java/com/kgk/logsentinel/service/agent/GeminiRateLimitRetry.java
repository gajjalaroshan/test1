package com.kgk.logsentinel.service.agent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GeminiRateLimitRetry {

    private static final Pattern RETRY_AFTER_SECONDS =
            Pattern.compile("retry in ([\\d.]+)s", Pattern.CASE_INSENSITIVE);

    private GeminiRateLimitRetry() {}

    static boolean isRateLimited(Throwable throwable) {
        String message = collectMessage(throwable);
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("429")
                || lower.contains("quota exceeded")
                || lower.contains("resource_exhausted")
                || lower.contains("rate limit");
    }

    static long retryDelayMillis(Throwable throwable) {
        String message = collectMessage(throwable);
        if (message != null) {
            Matcher matcher = RETRY_AFTER_SECONDS.matcher(message);
            if (matcher.find()) {
                double seconds = Double.parseDouble(matcher.group(1));
                long millis = (long) (seconds * 1000) + 500;
                return Math.min(millis, 12_000);
            }
        }
        return 6500;
    }

    private static String collectMessage(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null) {
                if (!sb.isEmpty()) {
                    sb.append(' ');
                }
                sb.append(current.getMessage());
            }
            current = current.getCause();
        }
        return sb.isEmpty() ? null : sb.toString();
    }
}
