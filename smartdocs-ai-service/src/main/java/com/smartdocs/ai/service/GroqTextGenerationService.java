package com.smartdocs.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartdocs.ai.service.strategy.TextGenerationStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * Groq (Llama 3.3 70B) implementation of TextGenerationStrategy
 *
 * CONCEPT: Strategy Pattern — Alternative Concrete Strategy
 * - Uses Groq's free API with OpenAI-compatible format
 * - Blazing fast inference (~500 tokens/sec)
 * - Llama 3.3 70B is excellent for RAG answer extraction
 * - Free tier: 30 RPM, 14.4K tokens/min
 *
 * WHY GROQ FOR RAG:
 * - Llama 3.3 70B follows instructions precisely (essential for RAG)
 * - Low latency means fast user responses
 * - Free tier rate limits are generous enough for development/personal use
 * - OpenAI-compatible API makes it easy to integrate
 */
@Slf4j
public class GroqTextGenerationService implements TextGenerationStrategy {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;
    private final String model;

    public GroqTextGenerationService(String apiKey, String baseUrl, String model) {
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
        return "Groq (" + model + ")";
    }

    /**
     * Generate text using Groq's OpenAI-compatible API
     *
     * CONCEPT: OpenAI-compatible Chat Completions API
     * - Groq follows the same request/response format as OpenAI
     * - Request: messages array with role + content
     * - Response: choices array with message content
     * - This makes it easy to swap between OpenAI-compatible providers
     */
    @Override
    public GenerationResult generateText(String prompt) {
        try {
            log.info("[{}] Calling for text generation...", getProviderName());
            log.info("Prompt length: {} characters", prompt.length());

            // Create request in OpenAI-compatible chat completion format
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of(
                                    "role", "user",
                                    "content", prompt)),
                    "temperature", 0.1, // Low creativity — we want precise RAG answers
                    "max_tokens", 1000, // Increased limit to ensure full answers are returned
                    "top_p", 0.8, // Focus on most likely responses
                    "stream", false // We want the complete response, not streamed
            );

            // Call Groq's OpenAI-compatible API
            String response = webClient
                    .post()
                    .uri(baseUrl + "/chat/completions")
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // Parse the OpenAI-format response
            return parseGroqResponse(response);

        } catch (Exception e) {
            log.error("[{}] Failed to generate text: {}", getProviderName(), e.getMessage(), e);
            return GenerationResult.failed("Text generation failed: " + e.getMessage());
        }
    }

    /**
     * Parse Groq/OpenAI-format response
     *
     * CONCEPT: OpenAI Response Format
     * Response structure:
     * {
     * "choices": [{
     * "message": {
     * "role": "assistant",
     * "content": "The generated text..."
     * }
     * }],
     * "usage": { "prompt_tokens": X, "completion_tokens": Y }
     * }
     */
    @SuppressWarnings("unchecked")
    private GenerationResult parseGroqResponse(String response) {
        try {
            log.debug("Raw response length: {}", response.length());

            Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);

            // Check for API errors
            if (responseMap.containsKey("error")) {
                Map<String, Object> error = (Map<String, Object>) responseMap.get("error");
                String errorMessage = (String) error.get("message");
                return GenerationResult.failed("Groq API error: " + errorMessage);
            }

            // Navigate OpenAI response structure: choices[0].message.content
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");

            if (choices == null || choices.isEmpty()) {
                return GenerationResult.failed("No choices in Groq response");
            }

            Map<String, Object> firstChoice = choices.get(0);
            Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");

            if (message == null) {
                return GenerationResult.failed("No message in choice");
            }

            String generatedText = (String) message.get("content");

            if (generatedText == null || generatedText.trim().isEmpty()) {
                return GenerationResult.failed("Empty generated text from Groq");
            }

            generatedText = generatedText.trim();

            // Log token usage for monitoring
            if (responseMap.containsKey("usage")) {
                Map<String, Object> usage = (Map<String, Object>) responseMap.get("usage");
                log.info("Token usage - Prompt: {}, Completion: {}, Total: {}",
                        usage.get("prompt_tokens"), usage.get("completion_tokens"), usage.get("total_tokens"));
            }

            log.info("[{}] Text generated successfully", getProviderName());
            log.info("Generated text: {}", generatedText);

            return GenerationResult.success(generatedText);

        } catch (Exception e) {
            log.error("Failed to parse Groq response: {}", e.getMessage(), e);
            return GenerationResult.failed("Parse error: " + e.getMessage());
        }
    }
}
