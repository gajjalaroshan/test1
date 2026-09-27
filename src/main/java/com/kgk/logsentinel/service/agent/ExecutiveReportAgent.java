package com.kgk.logsentinel.service.agent;

import com.kgk.logsentinel.config.LlmRuntimeConfig;
import com.kgk.logsentinel.dto.AgentStepResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

/**
 * Agent 2 — product-owner / leadership brief (business language only; no engineering evidence dumps).
 */
@Service
public class ExecutiveReportAgent {

    private static final String SYSTEM = """
            You are Executive Report Agent for Product Owners and business stakeholders.
            Produce a clear incident brief from the Java analysis metrics and flags only. Do NOT include engineering runbooks, stack traces, exception class names, log excerpts, trace dumps, or deep technical evidence (that belongs in the Remediation Planner for developers).

            REQUIREMENTS (follow exactly)
            1. Title and one-line summary in plain language (what customers or orders are affected).
            2. Executive summary: 3 short sentences — what happened, who is impacted, current status/risk.
            3. Business impact: bullets on failed operations volume (use counts from input), high-value or flagged orders, and customer-experience risk (no currency speculation unless amounts are in the data).
            4. Customer and order risk: call out FLAGGED_CUSTOMER / FLAGGED_ORDER items in non-technical terms (which accounts/orders need attention and why).
            5. Severity and confidence: one line each (INFO/WARN/CRITICAL and a justified confidence %).
            6. Likely causes (top 3): operational/business phrasing only (e.g., payment partner timeout), not Java types or stack details.
            7. Recommendations: 3–5 prioritized actions (P0/P1/P2) with accountable role (Operations, Engineering, Product) and ETA — outcome-focused, not debugging steps.
            8. Decisions needed from leadership: up to 2 bullets (decision + suggested deadline).

            FORMAT RULES
            - Plain business language throughout.
            - Human report as Markdown. Be complete; do not cut sections for brevity.
            - After the markdown, include one machine-readable JSON block in a fenced code block labeled json with keys: summary, impact, severity, confidence, customerOrderRisk[], topCauses[], recommendations[], decisions[]. Do NOT include an evidence object or technical log payload in JSON.
            - Do NOT truncate. Finish the full markdown and complete JSON.
            - End the entire output with the sentinel line exactly: ===END EXECUTIVE===

            Tone: urgent but calm, customer- and outcome-focused.
            """;

    private final ChatClient chatClient;
    private final LlmRuntimeConfig llmConfig;

    public ExecutiveReportAgent(ChatClient.Builder builder, LlmRuntimeConfig llmConfig) {
        this.chatClient = builder.build();
        this.llmConfig = llmConfig;
    }

    public AgentStepResult report(String javaSummary) {
        if (!llmConfig.isApiKeyConfigured()) {
            return AgentStepResult.skipped("Agent 2 skipped — LLM API key not configured");
        }
        try {
            ChatResponse response = chatClient
                    .prompt()
                    .system(SYSTEM)
                    .user("Java analysis (metrics and flags only — translate to business impact):\n" + javaSummary)
                    .call()
                    .chatResponse();
            Usage usage = response.getMetadata().getUsage();
            return AgentStepResult.success(
                    "executive-report",
                    response.getResult().getOutput().getText(),
                    tokens(usage));
        } catch (Exception e) {
            return AgentStepResult.failed("Agent 2 failed: " + e.getMessage());
        }
    }

    private static int tokens(Usage usage) {
        return usage != null ? (int) usage.getTotalTokens() : 0;
    }
}
