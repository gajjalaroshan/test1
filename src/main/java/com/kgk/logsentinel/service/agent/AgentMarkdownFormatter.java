package com.kgk.logsentinel.service.agent;

/**
 * Ensures agent markdown is readable in JSON and UIs (real newlines, not escaped {@code \\n} blobs).
 */
public final class AgentMarkdownFormatter {

    private AgentMarkdownFormatter() {}

    public static String normalize(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String normalized = text.replace("\r\n", "\n").replace("\\n", "\n").trim();
        if (!normalized.endsWith("\n")) {
            normalized = normalized + "\n";
        }
        return normalized;
    }
}
