package com.smartdocs.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartdocs.ai.service.strategy.EmbeddingStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * Google Embeddings implementation of EmbeddingStrategy
 *
 * CONCEPT: Strategy Pattern — Concrete Strategy for Embeddings
 * - Implements EmbeddingStrategy for Google's text-embedding API
 * - No @Service annotation — instantiated by LLMProviderFactory
 * - Produces 3072-dimensional vectors (gemini-embedding-001)
 */
@Slf4j
public class GoogleEmbeddingsService implements EmbeddingStrategy {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;
    private final String model;

    public GoogleEmbeddingsService(String apiKey, String baseUrl, String model) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(5 * 1024 * 1024))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getProviderName() {
        return "Google Embeddings (" + model + ")";
    }

    @Override
    public int getDimensions() {
        return 3072; // gemini-embedding-001 produces 3072-dimensional vectors
    }

    /**
     * Convert text to embedding vector using Google's API
     *
     * CONCEPT: This takes human-readable text and converts it to
     * a list of numbers (vector) that captures the meaning
     */
    @Override
    public EmbeddingResult generateEmbedding(String text) {
        try {
            log.info("[{}] Converting text to embedding vector...", getProviderName());
            log.info("Text: {}", (text.length() > 100 ? text.substring(0, 100) + "..." : text));

            // Create request for Google Embeddings API
            Map<String, Object> requestBody = Map.of(
                    "model", "models/" + model,
                    "content", Map.of(
                            "parts", new Object[] {
                                    Map.of("text", text)
                            }));

            // Call Google API to convert text to numbers
            String response = webClient
                    .post()
                    .uri(baseUrl + "/models/" + model + ":embedContent?key=" + apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // Parse the response to extract the vector of numbers
            return parseEmbeddingResponse(response);

        } catch (Exception e) {
            log.error("[{}] Failed to generate embedding: {}", getProviderName(), e.getMessage(), e);
            return EmbeddingResult.failed("Embedding generation failed: " + e.getMessage());
        }
    }

    /**
     * Parse Google's response and extract the vector of numbers
     */
    @SuppressWarnings("unchecked")
    private EmbeddingResult parseEmbeddingResponse(String response) {
        try {
            Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);

            // Navigate Google's response structure
            Map<String, Object> embedding = (Map<String, Object>) responseMap.get("embedding");
            if (embedding == null) {
                return EmbeddingResult.failed("No embedding in response");
            }

            List<Double> values = (List<Double>) embedding.get("values");
            if (values == null || values.isEmpty()) {
                return EmbeddingResult.failed("No embedding values found");
            }

            // Convert to float array for Pinecone
            float[] vector = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                vector[i] = values.get(i).floatValue();
            }

            log.info("[{}] Embedding generated: {} dimensions", getProviderName(), vector.length);
            log.info("First 5 numbers: [{}]",
                    String.format("%.3f, %.3f, %.3f, %.3f, %.3f...",
                            vector[0], vector[1], vector[2], vector[3], vector[4]));

            return EmbeddingResult.success(vector);

        } catch (Exception e) {
            log.error("Failed to parse embedding response: {}", e.getMessage(), e);
            return EmbeddingResult.failed("Parse error: " + e.getMessage());
        }
    }
}
