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
            Produce a clear incident brief from the EXECUTIVE METRICS payload only.

            HARD RULES (violations are unacceptable)
            - The payload has NO exception-type breakdown. Use ONLY: parsed event count, API failure count, failedOperations per customerId/orderId, maxAmountInr, and CUSTOMER_ATTENTION / ORDER_ATTENTION lines.
            - NEVER use words: Exception, NullPointer, IllegalState, SocketTimeout, stack, traceId, API_FAILURE, or any Java class name.
            - Do NOT invent a breakdown like "56 NullPointerExceptions" — that data is not in your input.
            - Business impact bullets: total failed operations (rollup), then per affected customerId/orderId using failedOperations and maxAmountInr only; then flagged-order amounts from ORDER_ATTENTION.
            - Likely causes: paraphrase ORDER_ATTENTION / CUSTOMER_ATTENTION reason text only (e.g. amount above threshold, many failure patterns in a short window). Do not invent payment-gateway or code-level root causes.
            - Recommendations: outcomes for Operations/Product/Risk (review flagged order, contact account owner, policy review) — not debugger tasks (fix null pointer, tune circuit breaker).
            - Confidence: use High/Medium/Low with one-sentence justification; never claim 100% unless the payload explicitly states certainty (it does not).
            - Translate each attention flag once — do not duplicate flag blocks.

            REQUIREMENTS (## headings in this order)
            1. Title and one-line summary (affected customerId / orderId from payload only).
            2. Executive summary: exactly 3 short sentences — volume of failed operations, who is impacted, business risk.
            3. Business impact: bullets as per HARD RULES (no exception taxonomy).
            4. Customer and order risk: plain language for each CUSTOMER_ATTENTION and ORDER_ATTENTION.
            5. Severity and confidence: INFO/WARN/CRITICAL plus High/Medium/Low confidence.
            6. Likely causes (top 3): operational, from flag reasons and volumes only.
            7. Recommendations: 3–5 outcome-focused actions with P0/P1/P2, role, ETA.
            8. Decisions needed from leadership: up to 2 bullets.

            FORMAT
            - Markdown only. No code fences. No JSON/YAML before or after the sentinel.
            - The last line of your entire response must be exactly: ===END EXECUTIVE===
            - Nothing may appear after ===END EXECUTIVE===

            Tone: urgent but calm, customer- and outcome-focused.
            """;

    private final ChatClient chatClient;
    private final LlmRuntimeConfig llmConfig;

    public ExecutiveReportAgent(ChatClient.Builder builder, LlmRuntimeConfig llmConfig) {
        this.chatClient = builder.build();
        this.llmConfig = llmConfig;
    }

    public AgentStepResult report(String executivePayload) {
        if (!llmConfig.isApiKeyConfigured()) {
            return AgentStepResult.skipped("Agent 2 skipped — LLM API key not configured");
        }
        try {
            ChatResponse response = chatClient
                    .prompt()
                    .system(SYSTEM)
                    .user("Executive metrics (authoritative — business translation only):\n" + executivePayload)
                    .call()
                    .chatResponse();
            Usage usage = response.getMetadata().getUsage();
            return AgentStepResult.success(
                    "executive-report",
                    AgentMarkdownFormatter.normalize(response.getResult().getOutput().getText()),
                    tokens(usage));
        } catch (Exception e) {
            return AgentStepResult.failed("Agent 2 failed: " + e.getMessage());
        }
    }

    private static int tokens(Usage usage) {
        return usage != null ? (int) usage.getTotalTokens() : 0;
    }
}
