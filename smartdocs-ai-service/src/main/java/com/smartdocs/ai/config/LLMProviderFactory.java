package com.smartdocs.ai.config;

import com.smartdocs.ai.service.GoogleEmbeddingsService;
import com.smartdocs.ai.service.GoogleTextGenerationService;
import com.smartdocs.ai.service.GroqTextGenerationService;
import com.smartdocs.ai.service.strategy.EmbeddingStrategy;
import com.smartdocs.ai.service.strategy.TextGenerationStrategy;
import lombok.extern.slf4j.Slf4j;

/**
 * Factory for creating LLM provider instances
 *
 * CONCEPT: Factory Pattern
 * - Encapsulates the creation logic for different LLM providers
 * - Takes provider name + config → returns the right strategy implementation
 * - Makes it easy to add new providers without changing consumer code
 *
 * DESIGN DECISION: Static factory methods instead of abstract factory
 * because we have a fixed set of providers and the creation logic is simple
 */
@Slf4j
public class LLMProviderFactory {

    /**
     * Create a TextGenerationStrategy based on provider name
     *
     * @param provider Provider name: "google" or "groq"
     * @param apiKey   API key for the provider
     * @param baseUrl  Base URL for the provider's API
     * @param model    Model name to use
     * @return Configured TextGenerationStrategy implementation
     * @throws IllegalArgumentException if provider is not supported
     */
    public static TextGenerationStrategy createTextGenerator(
            String provider, String apiKey, String baseUrl, String model) {

        return switch (provider.toLowerCase()) {
            case "google" -> {
                log.info("Factory: Creating Google Gemini text generation strategy");
                log.info("   Model: {}", model);
                yield new GoogleTextGenerationService(apiKey, baseUrl, model);
            }
            case "groq" -> {
                log.info("Factory: Creating Groq text generation strategy");
                log.info("   Model: {}", model);
                yield new GroqTextGenerationService(apiKey, baseUrl, model);
            }
            default -> throw new IllegalArgumentException(
                    "Unsupported text generation provider: '" + provider + "'. " +
                            "Supported providers: google, groq. " +
                            "Set 'llm.text-generation.provider' in application.yml.");
        };
    }

    /**
     * Create an EmbeddingStrategy based on provider name
     *
     * NOTE: Currently only Google is supported for embeddings.
     * Switching embedding providers requires re-indexing all documents in Pinecone
     * because different providers produce different vector dimensions.
     *
     * @param provider Provider name: "google"
     * @param apiKey   API key for the provider
     * @param baseUrl  Base URL for the provider's API
     * @param model    Model name to use
     * @return Configured EmbeddingStrategy implementation
     * @throws IllegalArgumentException if provider is not supported
     */
    public static EmbeddingStrategy createEmbeddingGenerator(
            String provider, String apiKey, String baseUrl, String model) {

        return switch (provider.toLowerCase()) {
            case "google" -> {
                log.info("Factory: Creating Google embedding strategy");
                log.info("   Model: {}", model);
                yield new GoogleEmbeddingsService(apiKey, baseUrl, model);
            }
            default -> throw new IllegalArgumentException(
                    "Unsupported embedding provider: '" + provider + "'. " +
                            "Supported providers: google. " +
                            "⚠️ Changing embedding providers requires re-indexing Pinecone! " +
                            "Set 'llm.embedding.provider' in application.yml.");
        };
    }
}
