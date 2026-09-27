package com.kgk.logsentinel.service.agent;

import com.kgk.logsentinel.config.LlmRuntimeConfig;
import com.kgk.logsentinel.dto.AgentStepResult;
import com.kgk.logsentinel.dto.LogAnalysisResult;
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
            Turn the supplied Java log analysis into a complete technical runbook.

            HARD RULES (violations are unacceptable)
            - Use ONLY customerId, orderId, traceId, counts, exception type labels, and flags from the payload. Never invent or round numbers.
            - Reproduce ERROR VOLUME & SPLITS exactly (totals and byType maps as given).
            - traceId: cite only ids that appear under byTraceId for that customer; at most 3 examples per customer, then state how many trace keys are listed in the payload. Never invent trace ids (e.g. do not write "e.g. 02b1ae...").
            - Do NOT fabricate log message quotes ("Cannot invoke...") — the payload has type counts, not message text unless explicitly provided.
            - Do NOT recommend specific libraries or products (Resilience4j, Bucket4j, JSR-303) unless named in the payload; describe the capability instead (circuit breaker, rate limit, input validation).
            - Cite each FLAGGED_* line once in section 4 — do not duplicate the RULE FLAGS block.
            - No leadership decisions, no PO executive summary.

            REQUIREMENTS (## headings in this order)
            1. Incident header: title + severity + technical rationale from exception mix and volumes.
            2. Evidence: totals; global and per-customer/order byType from payload; stackSignatureCounts; FLAGGED_* summary (one line each, not full duplicate block).
            3. Exception analysis: up to 6 types from global split — use simple type labels from payload (e.g. NullPointerException); symptoms tied to counts/customers/orders; mitigations grounded in type name, not invented stack quotes.
            4. Flagged entities playbook: per FLAGGED_CUSTOMER window and FLAGGED_ORDER.
            5. Engineering checklist: ≥5 items, owner + P0/P1/P2.
            6. Gaps/unknowns: missing path/message fields in payload.

            FORMAT
            - Markdown only. No ``` fences. No JSON/YAML. No content after the sentinel.
            - Last line exactly: ===END REMEDIATION===
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
                    .user("Java remediation payload (authoritative — cite exactly):\n" + javaResult.toRemediationPrompt())
                    .call()
                    .chatResponse();
            Usage usage = response.getMetadata().getUsage();
            return AgentStepResult.success(
                    "remediation-planner",
                    AgentMarkdownFormatter.normalize(response.getResult().getOutput().getText()),
                    tokens(usage));
        } catch (Exception e) {
            return AgentStepResult.failed("Agent 1 failed: " + e.getMessage());
        }
    }

    private static int tokens(Usage usage) {
        return usage != null ? (int) usage.getTotalTokens() : 0;
    }
}
