package com.smartdocs.ai.service.strategy;

/**
 * Strategy interface for embedding generation
 *
 * CONCEPT: Strategy Pattern for Embeddings
 * - Abstracts the embedding provider behind a common interface
 * - Currently only Google is implemented (switching requires Pinecone re-index)
 * - Designed for future extensibility (e.g., OpenAI, Cohere embeddings)
 */
public interface EmbeddingStrategy {

    /**
     * Convert text to an embedding vector using the configured provider
     */
    EmbeddingResult generateEmbedding(String text);

    /**
     * Get the name of the active embedding provider
     */
    String getProviderName();

    /**
     * Get the number of dimensions this provider's embeddings produce
     * Important: changing providers means different dimensions → requires Pinecone
     * re-index
     */
    int getDimensions();

    /**
     * Result of embedding generation — encapsulates success/failure states
     */
    class EmbeddingResult {
        private final float[] embedding;
        private final String errorMessage;
        private final boolean successful;

        private EmbeddingResult(float[] embedding, String errorMessage, boolean successful) {
            this.embedding = embedding;
            this.errorMessage = errorMessage;
            this.successful = successful;
        }

        public static EmbeddingResult success(float[] embedding) {
            return new EmbeddingResult(embedding, null, true);
        }

        public static EmbeddingResult failed(String errorMessage) {
            return new EmbeddingResult(null, errorMessage, false);
        }

        public float[] getEmbedding() {
            return embedding;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public boolean isSuccessful() {
            return successful;
        }

        public int getDimensions() {
            return embedding != null ? embedding.length : 0;
        }
    }
}
