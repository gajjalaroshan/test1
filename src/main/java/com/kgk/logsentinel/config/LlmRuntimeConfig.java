package com.kgk.logsentinel.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LlmRuntimeConfig {

    private static final String PLACEHOLDER = "placeholder-not-configured";

    private final String provider;
    private final String openAiApiKey;
    private final String geminiApiKey;
    private final String openAiModel;
    private final String geminiModel;

    public LlmRuntimeConfig(
            @Value("${logsentinel.llm.provider:gemini}") String provider,
            @Value("${spring.ai.openai.api-key:}") String openAiApiKey,
            @Value("${spring.ai.google.genai.api-key:}") String geminiApiKey,
            @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}") String openAiModel,
            @Value("${spring.ai.google.genai.chat.options.model:gemini-2.5-flash}") String geminiModel) {
        this.provider = provider.trim().toLowerCase();
        this.openAiApiKey = openAiApiKey;
        this.geminiApiKey = geminiApiKey;
        this.openAiModel = openAiModel;
        this.geminiModel = geminiModel;
    }

    public String provider() {
        return provider;
    }

    public String activeModelName() {
        return isOpenAi() ? openAiModel : geminiModel;
    }

    public String displayLabel() {
        return isOpenAi() ? "OpenAI (" + openAiModel + ")" : "Gemini (" + geminiModel + ")";
    }

    public boolean isOpenAi() {
        return "openai".equals(provider);
    }

    public boolean isGemini() {
        return "gemini".equals(provider) || "google-genai".equals(provider);
    }

    public boolean isApiKeyConfigured() {
        if (isOpenAi()) {
            return hasRealKey(openAiApiKey);
        }
        return hasRealKey(geminiApiKey);
    }

    public String missingKeyMessage() {
        if (isOpenAi()) {
            return "OPENAI_API_KEY is not configured. Set LLM_PROVIDER=openai and OPENAI_API_KEY in .env.";
        }
        return "GEMINI_API_KEY is not configured. Set LLM_PROVIDER=gemini and GEMINI_API_KEY in .env.";
    }

    private static boolean hasRealKey(String key) {
        return StringUtils.hasText(key) && !PLACEHOLDER.equals(key.trim());
    }
}
