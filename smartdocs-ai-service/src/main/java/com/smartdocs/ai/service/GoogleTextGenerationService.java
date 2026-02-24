package com.smartdocs.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartdocs.ai.service.strategy.TextGenerationStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * Google Gemini implementation of TextGenerationStrategy
 *
 * CONCEPT: Strategy Pattern — Concrete Strategy
 * - Implements the TextGenerationStrategy interface for Google Gemini
 * - No @Service annotation — instantiated by LLMProviderFactory
 * - All Gemini-specific API logic is encapsulated here
 */
@Slf4j
public class GoogleTextGenerationService implements TextGenerationStrategy {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;
    private final String model;

    public GoogleTextGenerationService(String apiKey, String baseUrl, String model) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getProviderName() {
        return "Google Gemini (" + model + ")";
    }

    /**
     * Generate text using Google Gemini API
     *
     * CONCEPT: Large Language Model (LLM) Text Generation
     * - Takes a prompt (instructions + context)
     * - Returns generated text based on the prompt
     * - Uses same API key as embeddings but different endpoint
     */
    @Override
    public GenerationResult generateText(String prompt) {
        try {
            log.info("[{}] Calling for text generation...", getProviderName());
            log.info("Prompt length: {} characters", prompt.length());

            // Create request for Google Generative AI API
            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(
                                    Map.of("text", prompt)))),
                    "generationConfig", Map.of(
                            "temperature", 0.1, // Low creativity - we want precise answers
                            "maxOutputTokens", 200, // Limit response length
                            "topP", 0.8, // Focus on most likely responses
                            "topK", 10 // Consider top 10 choices only
                    ),
                    "safetySettings", List.of(
                            Map.of("category", "HARM_CATEGORY_HARASSMENT", "threshold", "BLOCK_MEDIUM_AND_ABOVE"),
                            Map.of("category", "HARM_CATEGORY_HATE_SPEECH", "threshold", "BLOCK_MEDIUM_AND_ABOVE"),
                            Map.of("category", "HARM_CATEGORY_SEXUALLY_EXPLICIT", "threshold",
                                    "BLOCK_MEDIUM_AND_ABOVE"),
                            Map.of("category", "HARM_CATEGORY_DANGEROUS_CONTENT", "threshold",
                                    "BLOCK_MEDIUM_AND_ABOVE")));

            // Call Google Generative AI API
            String response = webClient
                    .post()
                    .uri(baseUrl + "/models/" + model + ":generateContent?key=" + apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // Parse the response to extract generated text
            return parseGenerationResponse(response);

        } catch (Exception e) {
            log.error("[{}] Failed to generate text: {}", getProviderName(), e.getMessage(), e);
            return GenerationResult.failed("Text generation failed: " + e.getMessage());
        }
    }

    /**
     * Parse Google's text generation response
     *
     * CONCEPT: API Response Parsing
     * Google returns complex JSON structure, we extract the actual generated text
     */
    @SuppressWarnings("unchecked")
    private GenerationResult parseGenerationResponse(String response) {
        try {
            log.debug("Raw response length: {}", response.length());

            Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);

            // Navigate Google's response structure
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) responseMap.get("candidates");

            if (candidates == null || candidates.isEmpty()) {
                return GenerationResult.failed("No candidates in response");
            }

            Map<String, Object> firstCandidate = candidates.get(0);
            Map<String, Object> content = (Map<String, Object>) firstCandidate.get("content");

            if (content == null) {
                return GenerationResult.failed("No content in candidate");
            }

            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");

            if (parts == null || parts.isEmpty()) {
                return GenerationResult.failed("No parts in content");
            }

            String generatedText = (String) parts.get(0).get("text");

            if (generatedText == null || generatedText.trim().isEmpty()) {
                return GenerationResult.failed("Empty generated text");
            }

            // Clean up the generated text
            generatedText = generatedText.trim();

            log.info("[{}] Text generated successfully", getProviderName());
            log.info("Generated text: {}", generatedText);

            return GenerationResult.success(generatedText);

        } catch (Exception e) {
            log.error("Failed to parse generation response: {}", e.getMessage(), e);
            return GenerationResult.failed("Parse error: " + e.getMessage());
        }
    }
}
