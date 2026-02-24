package com.smartdocs.ai.service.strategy;

/**
 * Strategy interface for LLM text generation
 *
 * CONCEPT: Strategy Pattern
 * - Defines a common contract for all text generation providers
 * - Allows swapping providers (Google Gemini, Groq, etc.) without changing
 * business logic
 * - Each provider implements this interface with provider-specific API calls
 */
public interface TextGenerationStrategy {

    /**
     * Generate text from a prompt using the configured LLM provider
     */
    GenerationResult generateText(String prompt);

    /**
     * Get the name of the active provider (for logging/debugging)
     */
    String getProviderName();

    /**
     * Result of text generation — encapsulates success/failure states
     */
    class GenerationResult {
        private final String text;
        private final String errorMessage;
        private final boolean successful;

        private GenerationResult(String text, String errorMessage, boolean successful) {
            this.text = text;
            this.errorMessage = errorMessage;
            this.successful = successful;
        }

        public static GenerationResult success(String text) {
            return new GenerationResult(text, null, true);
        }

        public static GenerationResult failed(String errorMessage) {
            return new GenerationResult(null, errorMessage, false);
        }

        public String getText() {
            return text;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public boolean isSuccessful() {
            return successful;
        }
    }
}
