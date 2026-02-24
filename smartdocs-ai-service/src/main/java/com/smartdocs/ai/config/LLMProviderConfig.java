package com.smartdocs.ai.config;

import com.smartdocs.ai.service.strategy.EmbeddingStrategy;
import com.smartdocs.ai.service.strategy.TextGenerationStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.extern.slf4j.Slf4j;

/**
 * Spring Configuration for LLM providers
 *
 * CONCEPT: Dependency Injection + Factory Pattern Integration
 * - Reads 'llm.text-generation.provider' and 'llm.embedding.provider' from
 * config
 * - Uses LLMProviderFactory to create the right beans
 * - Consumer services (@Autowired TextGenerationStrategy) get the configured
 * provider
 * - Switching providers = change one config value, restart app
 *
 * HOW THIS WORKS:
 * 1. Spring reads provider name from application.yml (e.g., "groq")
 * 2. This config class calls LLMProviderFactory with provider-specific config
 * 3. Factory returns the right implementation (e.g., GroqTextGenerationService)
 * 4. Spring registers it as a bean → all @Autowired TextGenerationStrategy use
 * it
 */
@Slf4j
@Configuration
public class LLMProviderConfig {

    // ═══════════════════════════════════════════════════════
    // Provider Selection (change these to switch providers)
    // ═══════════════════════════════════════════════════════

    @Value("${llm.text-generation.provider:groq}")
    private String textGenProvider;

    @Value("${llm.embedding.provider:google}")
    private String embeddingProvider;

    // ═══════════════════════════════════════════════════════
    // Google Configuration
    // ═══════════════════════════════════════════════════════

    @Value("${google.genai.api-key:}")
    private String googleGenaiApiKey;

    @Value("${google.genai.base-url:https://generativelanguage.googleapis.com/v1beta}")
    private String googleGenaiBaseUrl;

    @Value("${google.genai.model:gemini-2.0-flash-lite}")
    private String googleGenaiModel;

    @Value("${google.embeddings.api-key:}")
    private String googleEmbeddingsApiKey;

    @Value("${google.embeddings.base-url:https://generativelanguage.googleapis.com/v1beta}")
    private String googleEmbeddingsBaseUrl;

    @Value("${google.embeddings.model:gemini-embedding-001}")
    private String googleEmbeddingsModel;

    // ═══════════════════════════════════════════════════════
    // Groq Configuration
    // ═══════════════════════════════════════════════════════

    @Value("${groq.api-key:}")
    private String groqApiKey;

    @Value("${groq.base-url:https://api.groq.com/openai/v1}")
    private String groqBaseUrl;

    @Value("${groq.model:llama-3.3-70b-versatile}")
    private String groqModel;

    // ═══════════════════════════════════════════════════════
    // Bean Definitions (Factory creates the right implementation)
    // ═══════════════════════════════════════════════════════

    /**
     * Creates the active TextGenerationStrategy bean
     *
     * Spring will inject this wherever @Autowired TextGenerationStrategy is used
     */
    @Bean
    public TextGenerationStrategy textGenerationStrategy() {
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("CONFIGURING TEXT GENERATION PROVIDER");
        log.info("   Selected provider: {}", textGenProvider);

        String apiKey;
        String baseUrl;
        String model;

        // Resolve provider-specific config values
        switch (textGenProvider.toLowerCase()) {
            case "google" -> {
                apiKey = googleGenaiApiKey;
                baseUrl = googleGenaiBaseUrl;
                model = googleGenaiModel;
            }
            case "groq" -> {
                apiKey = groqApiKey;
                baseUrl = groqBaseUrl;
                model = groqModel;
            }
            default -> throw new IllegalArgumentException(
                    "Unknown text generation provider: " + textGenProvider);
        }

        TextGenerationStrategy strategy = LLMProviderFactory.createTextGenerator(
                textGenProvider, apiKey, baseUrl, model);

        log.info("   API Key loaded: {}",
                (apiKey != null && apiKey.length() > 4 ? apiKey.substring(0, 4) + "..." : "EMPTY OR NULL"));
        log.info("   Active provider: {}", strategy.getProviderName());
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        return strategy;
    }

    /**
     * Creates the active EmbeddingStrategy bean
     *
     * Spring will inject this wherever @Autowired EmbeddingStrategy is used
     */
    @Bean
    public EmbeddingStrategy embeddingStrategy() {
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("CONFIGURING EMBEDDING PROVIDER");
        log.info("   Selected provider: {}", embeddingProvider);

        String apiKey;
        String baseUrl;
        String model;

        // Resolve provider-specific config values
        switch (embeddingProvider.toLowerCase()) {
            case "google" -> {
                apiKey = googleEmbeddingsApiKey;
                baseUrl = googleEmbeddingsBaseUrl;
                model = googleEmbeddingsModel;
            }
            default -> throw new IllegalArgumentException(
                    "Unknown embedding provider: " + embeddingProvider);
        }

        EmbeddingStrategy strategy = LLMProviderFactory.createEmbeddingGenerator(
                embeddingProvider, apiKey, baseUrl, model);

        log.info("   Active provider: {}", strategy.getProviderName());
        log.info("   Dimensions: {}", strategy.getDimensions());
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        return strategy;
    }
}
