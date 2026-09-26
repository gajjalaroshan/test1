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
    private final String ollamaModel;

    public LlmRuntimeConfig(
            @Value("${logsentinel.llm.provider:gemini}") String provider,
            @Value("${spring.ai.openai.api-key:}") String openAiApiKey,
            @Value("${spring.ai.google.genai.api-key:}") String geminiApiKey,
            @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}") String openAiModel,
            @Value("${spring.ai.google.genai.chat.options.model:gemini-2.5-flash}") String geminiModel,
            @Value("${spring.ai.ollama.chat.options.model:gemma:2b}") String ollamaModel) {
        this.provider = provider.trim().toLowerCase();
        this.openAiApiKey = openAiApiKey;
        this.geminiApiKey = geminiApiKey;
        this.openAiModel = openAiModel;
        this.geminiModel = geminiModel;
        this.ollamaModel = ollamaModel;
    }

    public String provider() {
        return provider;
    }

    public String activeModelName() {
        if (isOpenAi()) {
            return openAiModel;
        }
        return isOllama() ? ollamaModel : geminiModel;
    }

    public String displayLabel() {
        if (isOpenAi()) {
            return "OpenAI (" + openAiModel + ")";
        }
        return isOllama() ? "Ollama (" + ollamaModel + ")" : "Gemini (" + geminiModel + ")";
    }

    public boolean isOpenAi() {
        return "openai".equals(provider);
    }

    public boolean isGemini() {
        return "gemini".equals(provider) || "google-genai".equals(provider);
    }

    public boolean isOllama() {
        return "ollama".equals(provider);
    }

    public boolean isApiKeyConfigured() {
        if (isOpenAi()) {
            return hasRealKey(openAiApiKey);
        }
        if (isOllama()) {
            return true;
        }
        return hasRealKey(geminiApiKey);
    }

    public String missingKeyMessage() {
        if (isOpenAi()) {
            return "OPENAI_API_KEY is not configured. Set LLM_PROVIDER=openai and OPENAI_API_KEY in .env.";
        }
        if (isOllama()) {
            return "Ollama is selected. Make sure Ollama is running and the configured model is available.";
        }
        return "GEMINI_API_KEY is not configured. Set LLM_PROVIDER=gemini and GEMINI_API_KEY in .env.";
    }

    private static boolean hasRealKey(String key) {
        return StringUtils.hasText(key) && !PLACEHOLDER.equals(key.trim());
    }
}
