package com.smartdocs.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartdocs.ai.service.strategy.EmbeddingStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

@Slf4j
@Service
public class PineconeVectorStoreService {

    @Autowired
    private EmbeddingStrategy embeddingStrategy; // Strategy pattern — provider determined by config

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${pinecone.api-key}")
    private String pineconeApiKey;

    @Value("${pinecone.index-name}")
    private String indexName;

    @Value("${pinecone.base-url}")
    private String pineconeBaseUrl;

    public PineconeVectorStoreService() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Store document with embeddings in Pinecone
     */
    public void storeDocument(String documentId, String userEmail, String text,
            String documentType, Double confidenceScore) {
        try {
            log.info("STORING DOCUMENT IN PINECONE");
            log.info("   Document ID: {}", documentId);
            log.info("   User: {}", userEmail);
            log.info("    Type: {}", documentType);

            // 1. Generate embeddings using the configured embedding strategy
            log.info("Step 1: Generating embeddings via {}...", embeddingStrategy.getProviderName());
            EmbeddingStrategy.EmbeddingResult embeddingResult = embeddingStrategy.generateEmbedding(text);

            if (!embeddingResult.isSuccessful()) {
                throw new RuntimeException("Failed to generate embeddings: " + embeddingResult.getErrorMessage());
            }

            float[] embedding = embeddingResult.getEmbedding();
            log.info("Embedding generated: {} dimensions", embedding.length);

            // 2. Prepare lightweight metadata for Pinecone (avoid 40KB limit)
            // Full text is stored in Supabase, not Pinecone
            Map<String, Object> metadata = Map.of(
                    "user_email", userEmail,
                    "document_type", documentType,
                    "confidence_score", confidenceScore.toString(),
                    "model", embeddingStrategy.getProviderName(),
                    "stored_at", String.valueOf(System.currentTimeMillis()));

            // 3. Convert float[] to List<Float> for Pinecone API
            List<Float> embeddingList = new ArrayList<>();
            for (float value : embedding) {
                embeddingList.add(value);
            }

            // 4. Create Pinecone upsert request
            Map<String, Object> vector = Map.of(
                    "id", documentId,
                    "values", embeddingList,
                    "metadata", metadata);

            Map<String, Object> pineconeRequest = Map.of(
                    "vectors", Arrays.asList(vector));

            // 5. Store in Pinecone
            log.info("Step 2: Storing in Pinecone...");

            String response = webClient.post()
                    .uri(pineconeBaseUrl + "/vectors/upsert")
                    .header("Api-Key", pineconeApiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(pineconeRequest)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("Document stored successfully in Pinecone!");
            log.info("Architecture: Google gemini-embedding-001 → Pinecone");

        } catch (Exception e) {
            log.error("Failed to store document in Pinecone: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to store document in Pinecone", e);
        }
    }

    /**
     * Search for similar documents
     */
    public List<SimilarDocument> searchSimilar(String query, String userEmail, int maxResults) {
        try {
            log.info("SEARCHING SIMILAR DOCUMENTS");
            log.info("   Query: '{}'", query);
            log.info("   User: {}", userEmail);

            // 1. Generate query embedding using configured provider
            EmbeddingStrategy.EmbeddingResult queryEmbeddingResult = embeddingStrategy
                    .generateEmbedding(query);

            if (!queryEmbeddingResult.isSuccessful()) {
                throw new RuntimeException(
                        "Failed to generate query embedding: " + queryEmbeddingResult.getErrorMessage());
            }

            float[] queryEmbedding = queryEmbeddingResult.getEmbedding();
            log.info("Query embedding generated: {} dimensions", queryEmbedding.length);

            // 2. Convert to List<Float> for Pinecone API
            List<Float> queryEmbeddingList = new ArrayList<>();
            for (float value : queryEmbedding) {
                queryEmbeddingList.add(value);
            }

            // 3. Create Pinecone search request
            Map<String, Object> filter = Map.of("user_email", userEmail);

            Map<String, Object> searchRequest = Map.of(
                    "vector", queryEmbeddingList,
                    "topK", maxResults,
                    "includeMetadata", true,
                    "includeValues", false,
                    "filter", filter);

            // 4. Search Pinecone
            log.info("Step 2: Querying Pinecone...");

            String response = webClient.post()
                    .uri(pineconeBaseUrl + "/query")
                    .header("Api-Key", pineconeApiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(searchRequest)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // 5. Parse and return results
            return parsePineconeSearchResponse(response);

        } catch (Exception e) {
            log.error("Search failed: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Parse Pinecone search response
     */
    private List<SimilarDocument> parsePineconeSearchResponse(String response) {
        try {
            List<SimilarDocument> results = new ArrayList<>();

            JsonNode responseNode = objectMapper.readTree(response);
            JsonNode matches = responseNode.get("matches");

            if (matches != null && matches.isArray()) {
                for (JsonNode match : matches) {
                    String documentId = match.get("id").asText();
                    double score = match.get("score").asDouble();

                    JsonNode metadata = match.get("metadata");
                    String documentType = metadata.get("document_type").asText();

                    // Text is no longer stored in Pinecone — will be fetched from Supabase
                    results.add(new SimilarDocument(
                            documentId,
                            null, // text fetched from Supabase later
                            documentType,
                            score));

                    log.info("   {} (similarity: {})", documentType, String.format("%.1f%%", score * 100));
                }
            }

            log.info("Found {} similar documents", results.size());
            return results;

        } catch (Exception e) {
            log.error("Failed to parse Pinecone response: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Test Pinecone connection and get index stats
     */
    public boolean testConnection() {
        try {
            log.info("Testing Pinecone connection...");

            String response = webClient.post()
                    .uri(pineconeBaseUrl + "/describe_index_stats")
                    .header("Api-Key", pineconeApiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(Map.of())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("Pinecone connection successful!");
            log.info("Index stats: {}", response);
            return true;

        } catch (Exception e) {
            log.error("Pinecone connection failed: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Get service statistics
     */
    public Map<String, Object> getStats() {
        return Map.of(
                "embedding_service", "Google gemini-embedding-001",
                "vector_store", "Pinecone",
                "index_name", indexName,
                "dimensions", 3072,
                "status", "connected");
    }

    /**
     * Similar document result class
     */
    public static class SimilarDocument {
        private final String documentId;
        private String text; // Mutable — set after Supabase lookup
        private final String documentType;
        private final double similarity;

        public SimilarDocument(String documentId, String text, String documentType, double similarity) {
            this.documentId = documentId;
            this.text = text;
            this.documentType = documentType;
            this.similarity = similarity;
        }

        public String getDocumentId() {
            return documentId;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public String getDocumentType() {
            return documentType;
        }

        public double getSimilarity() {
            return similarity;
        }

        @Override
        public String toString() {
            return String.format("SimilarDocument{id='%s', type='%s', similarity=%.1f%%}",
                    documentId, documentType, similarity * 100);
        }
    }
}
