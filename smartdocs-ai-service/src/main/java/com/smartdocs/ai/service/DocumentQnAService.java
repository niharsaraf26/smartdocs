package com.smartdocs.ai.service;

import com.smartdocs.common.entity.Document;
import com.smartdocs.common.entity.DocumentMetadata;
import com.smartdocs.common.repository.DocumentMetadataRepository;
import com.smartdocs.common.repository.DocumentRepository;
import com.smartdocs.ai.service.PineconeVectorStoreService.SimilarDocument;
import com.smartdocs.ai.service.RoutingLLMService.RoutingResult;
import com.smartdocs.ai.service.strategy.TextGenerationStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class DocumentQnAService {

    @Autowired
    private PineconeVectorStoreService vectorService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentMetadataRepository metadataRepository;

    @Autowired
    private TextGenerationStrategy textGenerationStrategy;

    @Autowired
    private QueryRouter queryRouter;

    @Value("${qna.context.max-chars:8000}")
    private int maxContextChars;

    /**
     * Smart-routed question answering:
     * FACTUAL → SQL metadata lookup (NO LLM — direct return)
     * SEMANTIC → Pinecone RAG + LLM answer generation
     * CROSS_DOCUMENT → SQL aggregation + LLM analysis
     */
    public AnswerResult answerQuestion(String query, String userEmail) {
        log.info("HYBRID RAG PIPELINE");
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("Question: {}", query);
        log.info("User: {}", userEmail);

        RoutingResult routing = queryRouter.classify(query);

        try {
            return switch (routing.type()) {
                case FACTUAL -> answerFromSQL(routing.fieldHints(), query, userEmail);
                case CROSS_DOCUMENT -> answerFromCrossDocument(query, userEmail, routing.documentTypes());
                case SEMANTIC -> answerFromPinecone(query, userEmail);
            };
        } catch (Exception e) {
            log.error("Pipeline failed: {}", e.getMessage(), e);
            return AnswerResult.error("An error occurred while processing your question.");
        }
    }

    // FACTUAL: Direct SQL Metadata Lookup (NO LLM)
    private AnswerResult answerFromSQL(List<String> fieldHints, String query, String userEmail) {
        log.info("\n→ FACTUAL: SQL metadata lookup (no LLM)");

        List<DocumentMetadata> results = new ArrayList<>();

        // 1. Search for each field hint from the router LLM
        if (fieldHints != null && !fieldHints.isEmpty()) {
            for (String fieldHint : fieldHints) {
                log.info("   Searching metadata by field hint: '{}'", fieldHint);

                // Try exact match first
                List<DocumentMetadata> matches = metadataRepository
                        .findByUserEmailAndFieldNameIgnoreCase(userEmail, fieldHint);

                // Try fuzzy match (e.g. "name" matches "person_name", "mothers_name")
                if (matches.isEmpty()) {
                    matches = metadataRepository
                            .findByUserEmailAndFieldNameContainingIgnoreCase(userEmail, fieldHint);
                }

                results.addAll(matches);
            }
        }

        // 2. Also try searching by value as a last resort before fallback
        if (results.isEmpty()) {
            String cleanedQuery = query.replaceAll("[?!.]", "").trim();
            String[] words = cleanedQuery.split("\\s+");
            if (words.length > 0) {
                String searchTerm = words[words.length - 1];
                if (searchTerm.length() > 2) {
                    results = metadataRepository.searchByFieldValue(userEmail, searchTerm);
                }
            }
        }

        if (results.isEmpty()) {
            log.info("   No metadata match — falling back to SEMANTIC search");
            return answerFromPinecone(query, userEmail);
        }

        // 3. Format answer directly — NO LLM call
        log.info("   Found {} matching metadata fields", results.size());
        String answer = formatFactualAnswer(results);

        return AnswerResult.success(answer, "FACTUAL", List.of());
    }

    /**
     * Format SQL metadata results into a plain-text answer.
     * No LLM needed — the data is already structured.
     */
    private String formatFactualAnswer(List<DocumentMetadata> results) {
        if (results.size() == 1) {
            DocumentMetadata meta = results.get(0);
            return String.format("%s (from %s)", meta.getFieldValue(), meta.getDocumentType());
        }

        // Multiple results — list them all
        StringBuilder sb = new StringBuilder();
        for (DocumentMetadata meta : results) {
            sb.append(String.format("- %s: %s (from %s)\n",
                    meta.getFieldName(), meta.getFieldValue(), meta.getDocumentType()));
        }
        return sb.toString().trim();
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // CROSS_DOCUMENT: SQL Aggregation + LLM Analysis
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private AnswerResult answerFromCrossDocument(String query, String userEmail, List<String> documentTypeHints) {
        log.info("\n→ CROSS_DOCUMENT: Type-filtered full-text aggregation");

        // Step 1: Resolve which documents to load based on type hints from the router
        List<Document> targetDocs;
        if (documentTypeHints != null && !documentTypeHints.isEmpty()) {
            log.info("   Document type filter: {}", documentTypeHints);
            targetDocs = documentRepository.findByUserEmailAndProcessingStatusAndDocumentTypeIn(
                    userEmail, Document.ProcessingStatus.COMPLETED, documentTypeHints);

            // If no docs matched the filter, fall back to ALL completed docs
            if (targetDocs.isEmpty()) {
                log.warn("   No docs matched type filter — falling back to all completed docs");
                targetDocs = documentRepository.findByUserEmailAndProcessingStatus(
                        userEmail, Document.ProcessingStatus.COMPLETED);
            }
        } else {
            log.info("   No type hint — loading all completed docs");
            targetDocs = documentRepository.findByUserEmailAndProcessingStatus(
                    userEmail, Document.ProcessingStatus.COMPLETED);
        }

        if (targetDocs.isEmpty()) {
            return AnswerResult
                    .notFound(
                            "I don't have any processed documents to answer your question. Please upload and process some documents first.");
        }

        log.info("   Found {} relevant documents", targetDocs.size());

        // Step 2: Build context from the full extracted text of each document
        // This is far more reliable than metadata key-value pairs for
        // aggregation/comparison
        StringBuilder context = new StringBuilder();
        int includedDocs = 0;
        for (Document doc : targetDocs) {
            if (doc.getExtractedText() == null || doc.getExtractedText().isBlank()) {
                log.warn("   Doc {} has no extracted text, skipping", doc.getId());
                continue;
            }

            String normalizedText = normalizeWhitespace(doc.getExtractedText());
            String section = String.format("=== %s (file: %s) ===\n%s\n\n",
                    doc.getDocumentType() != null ? doc.getDocumentType() : "Unknown",
                    doc.getOriginalFilename(),
                    normalizedText);

            if (context.length() + section.length() > maxContextChars) {
                log.warn("   Context budget reached after {} docs ({} chars). Remaining docs skipped.",
                        includedDocs, maxContextChars);
                break;
            }
            context.append(section);
            includedDocs++;
        }

        if (context.isEmpty()) {
            return AnswerResult.noAnswer("Found matching documents but couldn't retrieve their content.");
        }

        log.info("   Built context from {} documents ({} chars)", includedDocs, context.length());

        // Step 3: Send full text to the big LLM for comparison/aggregation
        String prompt = buildCrossDocPrompt(query, context.toString());
        String answer = callLLM(prompt);

        return AnswerResult.success(answer, "CROSS_DOCUMENT", List.of());
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // SEMANTIC: Pinecone RAG (vector search + LLM)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private AnswerResult answerFromPinecone(String query, String userEmail) {
        log.info("\n→ SEMANTIC: Pinecone RAG pipeline");

        // 1. Pinecone semantic search
        List<SimilarDocument> matches = vectorService.searchSimilar(query, userEmail, 3);
        if (matches.isEmpty()) {
            return AnswerResult.notFound("I couldn't find any documents to answer your question.");
        }

        // 2. Fetch full text from Supabase
        log.info("   Fetching full text from Supabase...");
        for (SimilarDocument match : matches) {
            try {
                UUID docId = UUID.fromString(match.getDocumentId());
                Optional<Document> doc = documentRepository.findById(docId);
                if (doc.isPresent() && doc.get().getExtractedText() != null) {
                    match.setText(doc.get().getExtractedText());
                }
            } catch (Exception e) {
                log.warn("   Failed to fetch: {}", match.getDocumentId());
            }
        }

        // 3. Build context from full text (whitespace-normalized, capped at
        // maxContextChars)
        String context = buildContextFromDocuments(matches);
        if (context.isBlank()) {
            return AnswerResult.noAnswer("I found matching documents but couldn't retrieve their content.");
        }

        // 4. Generate answer
        String prompt = buildPrompt(query, context);
        String answer = callLLM(prompt);

        log.info("   Answer: {}", answer);
        return AnswerResult.success(answer, "SEMANTIC", matches);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Shared Utilities
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * Normalize excessive whitespace in text.
     * OCR-extracted text often has extra spaces and blank lines that waste
     * context budget without adding information.
     */
    private String normalizeWhitespace(String text) {
        if (text == null)
            return "";
        // Collapse multiple spaces/tabs to single space, collapse multiple newlines to
        // double
        return text.replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String buildContextFromDocuments(List<SimilarDocument> documents) {
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            SimilarDocument doc = documents.get(i);
            if (doc.getText() == null || doc.getText().isBlank())
                continue;

            String normalizedText = normalizeWhitespace(doc.getText());
            String section = String.format("=== Document %d (%s) ===\n%s\n\n",
                    i + 1, doc.getDocumentType(), normalizedText);

            if (context.length() + section.length() > maxContextChars) {
                log.warn("   Context truncated at document {} (limit: {} chars)", i + 1, maxContextChars);
                break;
            }
            context.append(section);
        }
        return context.toString();
    }

    private String buildPrompt(String query, String context) {
        return String.format(
                """
                        You are a professional enterprise document retrieval assistant.
                        Answer the user's question based ONLY on the provided information.
                        Be extremely concise, direct, and to the point. Do not add filler words, conversational fluff, or unsolicited advice.
                        Respond with the exact answer requested.
                        If you cannot find the answer in the provided documents, reply exactly with: "ANSWER_NOT_FOUND"

                        USER QUESTION: %s

                        AVAILABLE INFORMATION:
                        %s

                        ANSWER:
                        """,
                query, context);
    }

    private String buildCrossDocPrompt(String query, String context) {
        return String.format(
                """
                        You are a professional enterprise data analyst evaluating multiple documents.
                        Compare and correlate the user's documents based ONLY on the provided metadata.
                        Be extremely concise, direct, and to the point. No fluff or conversational filler.
                        Answer only what was asked. If comparing, state the exact values from each document and the conclusion clearly and briefly.
                        If the required information is missing, reply exactly with: "ANSWER_NOT_FOUND"

                        USER QUESTION: %s

                        USER'S DOCUMENTS AND METADATA:
                        %s

                        ANALYSIS:
                        """,
                query, context);
    }

    private String callLLM(String prompt) {
        try {
            TextGenerationStrategy.GenerationResult result = textGenerationStrategy.generateText(prompt);
            if (!result.isSuccessful())
                return "I'm sorry, I encountered an issue processing your request.";

            String response = result.getText().trim();

            // Check for "not found" patterns
            if (response.toLowerCase().contains("answer_not_found") ||
                    response.toLowerCase().contains("i don't have") ||
                    response.toLowerCase().contains("cannot find")) {
                return "I searched through your documents but couldn't find that specific information.";
            }

            return response;
        } catch (Exception e) {
            log.error("LLM call failed: {}", e.getMessage(), e);
            return "I apologize, but I'm having trouble accessing your documents right now.";
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Result class
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    public static class AnswerResult {
        private final String answer;
        private final String routeType;
        private final List<SimilarDocument> sourceDocuments;
        private final String status;
        private final String message;

        private AnswerResult(String answer, String routeType, List<SimilarDocument> sourceDocuments,
                String status, String message) {
            this.answer = answer;
            this.routeType = routeType;
            this.sourceDocuments = sourceDocuments;
            this.status = status;
            this.message = message;
        }

        public static AnswerResult success(String answer, String routeType, List<SimilarDocument> sources) {
            return new AnswerResult(answer, routeType, sources, "SUCCESS", null);
        }

        public static AnswerResult notFound(String message) {
            return new AnswerResult(null, null, List.of(), "NOT_FOUND", message);
        }

        public static AnswerResult noAnswer(String message) {
            return new AnswerResult(null, null, List.of(), "NO_ANSWER", message);
        }

        public static AnswerResult error(String message) {
            return new AnswerResult(null, null, List.of(), "ERROR", message);
        }

        public String getAnswer() {
            return answer;
        }

        public String getRouteType() {
            return routeType;
        }

        public List<SimilarDocument> getSourceDocuments() {
            return sourceDocuments;
        }

        public String getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }

        public boolean isSuccessful() {
            return "SUCCESS".equals(status);
        }
    }
}
