package com.kgk.logsentinel.service.agent;

import java.util.regex.Pattern;

/**
 * Normalizes agent markdown for APIs and UIs (real newlines, not escaped {@code \\n} blobs).
 * Strips JSON/code-fence appendices models sometimes add despite prompt rules.
 */
public final class AgentMarkdownFormatter {

    private static final Pattern JSON_FENCE = Pattern.compile("(?s)```json\\s*.*?```\\s*");
    private static final Pattern ANY_FENCE = Pattern.compile("(?s)```\\s*.*?```\\s*");

    private AgentMarkdownFormatter() {}

    public static String normalize(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String normalized = text.replace("\r\n", "\n").replace("\\n", "\n").trim();
        normalized = stripModelAppendices(normalized);
        normalized = trimAfterSentinel(normalized);
        if (!normalized.endsWith("\n")) {
            normalized = normalized + "\n";
        }
        return normalized;
    }

    static String stripModelAppendices(String text) {
        String out = JSON_FENCE.matcher(text).replaceAll("");
        out = ANY_FENCE.matcher(out).replaceAll("");
        return out.trim();
    }

    private static String trimAfterSentinel(String text) {
        String remediation = "===END REMEDIATION===";
        String executive = "===END EXECUTIVE===";
        int r = text.lastIndexOf(remediation);
        int e = text.lastIndexOf(executive);
        int end = Math.max(r, e);
        if (end < 0) {
            return text;
        }
        int len = r >= e ? remediation.length() : executive.length();
        return text.substring(0, end + len).trim();
    }
}
