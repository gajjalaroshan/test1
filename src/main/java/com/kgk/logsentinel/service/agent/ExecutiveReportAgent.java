package com.kgk.logsentinel.service.agent;

import com.kgk.logsentinel.config.LlmRuntimeConfig;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

@Service
public class ExecutiveReportAgent {

    private static final String SYSTEM = """
            You are Executive Report Agent. Produce a one-page, business-friendly incident report that removes coding-level detail and speaks clearly to executives, product, finance, and ops leads.

            REQUIREMENTS (follow exactly)
            1. Title line and a single-sentence one-line summary (<= 20 words).
            2. Executive summary: exactly 3 short sentences (high-level, plain English).
            3. Business impact: 3 bullets (estimated #errors, top-customer financial exposure, estimated potential revenue/financial risk).
            4. Severity and confidence: one-line severity (INFO/WARN/CRITICAL) and confidence as a percent.
            5. Top 3 root causes: each 1 short sentence, framed as business/operational causes (no stack traces, exception class names, or code-level detail).
            6. Priority remediation recommendations: 3 items labeled P0/P1/P2. Each item must be 1 line with owner role (e.g., Operations, Engineering, Site Reliability) and ETA (hours/days).
            7. Decision requests for executives: 2 bullets stating the decision required and a deadline.
            8. Tiny evidence section: 3 metrics (exception/event counts) and a single link/reference to the full machine-readable JSON artifact.

            FORMAT RULES
            - Use plain business language. Do NOT include stack traces, exception class names, error messages, code snippets, debugging steps, or detailed technical logs.
            - Translate technical findings into business impact and actionable next steps (e.g., payment connectivity outage, not Java exception names).
            - Human report must be concise and not exceed 300 words.
            - After the human report, include a machine-readable JSON block in a fenced code block labeled json with keys: summary, impact, severity, confidence, topRootCauses[], recommendations[], decisions[], evidence{}.
            - If content risks truncation: output the 300-word human report first, then only the complete JSON (no extra prose). Do NOT cut the JSON.
            - End the entire output with the sentinel line exactly: ===END EXECUTIVE===

            Tone: urgent but calm, business-focused, prioritized, and prescriptive (who, what, when).
            """;

    private final ChatClient chatClient;
    private final LlmRuntimeConfig llmConfig;

    public ExecutiveReportAgent(ChatClient.Builder builder, LlmRuntimeConfig llmConfig) {
        this.chatClient = builder.build();
        this.llmConfig = llmConfig;
    }

    public AgentStepResult report(String remediationPlan, String javaSummary) {
        if (!llmConfig.isApiKeyConfigured()) {
            return AgentStepResult.skipped("Agent 2 skipped — LLM API key not configured");
        }
        try {
            ChatResponse response = chatClient
                    .prompt()
                    .system(SYSTEM)
                    .user("Java analysis:\n"
                            + javaSummary
                            + "\n\nAgent 1 — Remediation plan:\n"
                            + remediationPlan)
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
