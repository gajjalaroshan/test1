package com.kgk.logsentinel.service.agent;

import com.kgk.logsentinel.domain.analysis.LogAnalysisResult;
import com.kgk.logsentinel.config.LlmRuntimeConfig;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

/**
 * Agent 1 — technical remediation runbook for engineering leads (not executive/product audiences).
 */
@Service
public class RemediationPlannerAgent {

    private static final String SYSTEM = """
            You are Remediation Planner Agent for an engineering lead / on-call developer audience.
            Turn the supplied Java log analysis into a complete technical runbook. Do NOT write product-owner or executive briefs (no business-financial narrative, no "decisions for leadership", no non-technical impact framing).

            REQUIREMENTS (follow exactly)
            1. Incident header: one-line title + severity (INFO/WARN/CRITICAL) with a technical rationale (what broke, which services/paths).
            2. Evidence (required, detailed): reproduce authoritative counts from the input exactly; cite customerId, orderId, traceId, exception types, stack-signature counts, and FLAGGED_* lines. Include short quoted error fields (exception class, message, path) from the payload when present. This section is for engineers — be specific and complete.
            3. For each exception type in the top 6 by count: symptoms, likely technical causes, immediate mitigation steps, durable fix, how to verify recovery, and affected customers/orders/traces.
            4. Flagged entities playbook: for each FLAGGED_CUSTOMER and FLAGGED_ORDER, concrete investigation and remediation steps (what to check in logs/traces, rollback/mitigation).
            5. Prioritized engineering checklist (at least 5 items) with owner role (Engineering/SRE/Platform) and ETA (P0/P1/P2).
            6. Gaps/unknowns: bullets for missing telemetry or ambiguous signals.

            FORMAT RULES
            - Human report as Markdown. Be thorough; do not omit sections to save length.
            - After the markdown, output one machine-readable JSON object in a fenced code block labeled json (keys: severity, evidence, issuesByType[], flaggedPlaybooks[], checklist[], gaps[]). JSON must be complete — never partial.
            - Do NOT truncate or abbreviate the report. If you approach output limits, finish the JSON block completely, then end.
            - Terminate the entire output with this sentinel line exactly: ===END REMEDIATION===
            """;

    private final ChatClient chatClient;
    private final LlmRuntimeConfig llmConfig;

    public RemediationPlannerAgent(ChatClient.Builder builder, LlmRuntimeConfig llmConfig) {
        this.chatClient = builder.build();
        this.llmConfig = llmConfig;
    }

    public AgentStepResult plan(LogAnalysisResult javaResult) {
        if (!llmConfig.isApiKeyConfigured()) {
            return AgentStepResult.skipped("Agent 1 skipped — LLM API key not configured");
        }
        try {
            ChatResponse response = chatClient
                    .prompt()
                    .system(SYSTEM)
                    .user("Java log analysis (authoritative — cite exactly in Evidence):\n" + javaResult.toAgentPrompt())
                    .call()
                    .chatResponse();
            Usage usage = response.getMetadata().getUsage();
            return AgentStepResult.success(
                    "remediation-planner",
                    response.getResult().getOutput().getText(),
                    tokens(usage));
        } catch (Exception e) {
            return AgentStepResult.failed("Agent 1 failed: " + e.getMessage());
        }
    }

    private static int tokens(Usage usage) {
        return usage != null ? (int) usage.getTotalTokens() : 0;
    }
}
