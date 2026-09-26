package com.kgk.logsentinel.service.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentMarkdownFormatterTest {

    @Test
    void expandsEscapedNewlines() {
        String raw = "## Severity\\n- HIGH\\n\\n## Impact\\n- cust-burst-1";
        String out = AgentMarkdownFormatter.normalize(raw);
        assertTrue(out.contains("## Severity\n- HIGH"));
        assertTrue(out.contains("cust-burst-1"));
    }

    @Test
    void preservesRealNewlines() {
        String raw = "## Flagged customers\n- customerId: cust-burst-1\n";
        assertEquals("## Flagged customers\n- customerId: cust-burst-1\n", AgentMarkdownFormatter.normalize(raw));
    }
}
