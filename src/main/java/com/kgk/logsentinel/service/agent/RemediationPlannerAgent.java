package com.kgk.logsentinel.service.agent;

import com.kgk.logsentinel.domain.analysis.LogAnalysisResult;
import com.kgk.logsentinel.config.LlmRuntimeConfig;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

/**
 * Agent 1 — turns Java metrics into an operator runbook (what to check and how to fix).
 * Does not replace Java counting; focuses on remediation and verification steps.
 */
@Service
public class RemediationPlannerAgent {

    private static final String SYSTEM = """
            You are Remediation Planner Agent. Produce a crisp, actionable remediation plan from the supplied analysis JSON. Output MUST contain both a compact human-readable markdown report for engineers/Ops and a machine-readable JSON object for automation.

            REQUIREMENTS (must follow exactly)
            1. Human summary: 2-4 sentence executive overview (one paragraph).
            2. Severity: one-line level (INFO/WARN/CRITICAL) + 1-sentence rationale.
            3. Impact summary: 3 bullet metrics: total API failures, top-3 exception types with counts and %s, top-3 affected customers/orders with counts.
            4. For each exception type (only those in top-6 by count) provide symptoms, HYPOTHESIS causes, immediate actions, durable fixes, verify recovery, and affected customers/orders.
            5. Global prioritized remediation checklist (3 items) with owner and ETA (P0/P1).
            6. Minimal Gaps/Unknowns (1-2 bullets).

            FORMAT RULES
            - Produce the human report as Markdown.
            - After the markdown, output a single machine-readable JSON block in a fenced code block labeled json.
            - Keep the human-readable section <= 400 words. Keep the JSON complete (full lists).
            - If full output would exceed model limits, emit the human summary + a compact JSON only; do NOT truncate JSON partially.
            - Terminate output with sentinel line exactly: ===END REMEDIATION===
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
                    .user(javaResult.toAgentPrompt())
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
