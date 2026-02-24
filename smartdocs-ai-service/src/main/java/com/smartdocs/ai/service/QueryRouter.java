package com.smartdocs.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Query router — classifies user questions using a lightweight LLM (Groq 8B).
 *
 * Routes to:
 * - FACTUAL: Direct SQL lookup on document_metadata (fast, no big LLM needed)
 * - SEMANTIC: Pinecone vector search + full text RAG (for complex questions)
 * - CROSS_DOCUMENT: SQL aggregation across all user documents (comparisons)
 *
 * Also extracts the field name for FACTUAL queries so the SQL lookup
 * knows exactly what to search for, eliminating hardcoded field mappings.
 */
@Slf4j
@Component
public class QueryRouter {

    public enum QueryType {
        FACTUAL, // "What is my PAN number?"
        SEMANTIC, // "Summarize my marksheet"
        CROSS_DOCUMENT // "Is my name the same on my Aadhar and marksheet?"
    }

    @Autowired
    private RoutingLLMService routingLLMService;

    /**
     * Classify user query into a query type + extract field hint.
     * Uses Groq llama-3.1-8b-instant for flexible, accurate classification.
     */
    public RoutingLLMService.RoutingResult classify(String query) {
        log.info("Routing query: '{}'", query);
        RoutingLLMService.RoutingResult result = routingLLMService.classifyQuery(query);
        log.info("Route decision: {} (fields: {})", result.type(), result.fieldHints());
        return result;
    }
}
