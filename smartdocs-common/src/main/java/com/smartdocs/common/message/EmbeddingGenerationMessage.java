package com.smartdocs.common.message;

import java.util.UUID;

public record EmbeddingGenerationMessage(
                UUID documentId,
                String userEmail,
                String extractedText, // Full verbatim text (stored in Supabase)
                String proseSummary, // Clean prose summary (embedded in Pinecone)
                String structuredFieldsJson, // JSON array of extracted key-value fields (stored in document_metadata)
                String documentType,
                Double confidenceScore) {
}
