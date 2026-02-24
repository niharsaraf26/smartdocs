package com.smartdocs.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartdocs.common.entity.Document;
import com.smartdocs.common.entity.DocumentMetadata;
import com.smartdocs.common.entity.DocumentType;
import com.smartdocs.common.message.DocumentProcessingMessage;
import com.smartdocs.common.repository.DocumentMetadataRepository;
import com.smartdocs.common.repository.DocumentRepository;
import com.smartdocs.common.service.SupabaseStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Unified Document Pipeline — handles the entire flow in one service:
 *
 * RabbitMQ trigger → Download file → Gemini extraction → Save metadata to SQL
 * → Embed prose summary in Pinecone
 * → Save full text to documents table
 *
 * Replaces the old 2-service flow:
 * ai-extraction-service (DocumentProcessingListener) → RabbitMQ →
 * vector-embedding-service (EmbeddingGenerationListener)
 */
@Slf4j
@Service
public class DocumentPipelineService {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentMetadataRepository metadataRepository;

    @Autowired
    private SupabaseStorageService storageService;

    @Autowired
    private GeminiService geminiService;

    @Autowired
    private PineconeVectorStoreService pineconeService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Entry point: triggered by document-service via RabbitMQ.
     * Processes the entire pipeline: extraction → metadata save → embedding.
     */
    @RabbitListener(queues = "document.processing")
    @Transactional
    public void processDocument(DocumentProcessingMessage message) {

        log.info("\nUNIFIED DOCUMENT PIPELINE");
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("Document ID: {}", message.documentId());
        log.info("User: {}", message.userEmail());
        log.info("📁 File: {}", message.originalFilename());
        log.info("Size: {}", formatFileSize(message.fileSize()));

        try {
            // 1. Find document in database
            Optional<Document> docOptional = documentRepository.findById(message.documentId());
            if (docOptional.isEmpty()) {
                log.error("Document not found: {}", message.documentId());
                return;
            }

            Document document = docOptional.get();
            document.markAsProcessing();
            documentRepository.save(document);

            // 2. Download file from Supabase Storage
            log.info("\n📥 STEP 1: Downloading file from storage...");
            byte[] fileBytes = storageService.downloadFile(message.storagePath());
            log.info("   Downloaded {}", formatFileSize((long) fileBytes.length));

            // 3. Process with Gemini AI (extraction)
            log.info("\nSTEP 2: Gemini AI extraction...");
            GeminiService.GeminiResponse result = geminiService.processDocument(
                    fileBytes, message.contentType(), message.originalFilename());

            if (!result.isSuccessful()) {
                document.markAsFailed();
                documentRepository.save(document);
                log.error("Gemini extraction failed: {}", result.getErrorMessage());
                return;
            }

            // 4. Normalize document type using the DocumentType enum
            String normalizedType = DocumentType.fromGeminiString(result.getDocumentType()).name();
            log.info("   Document type: {} → {}", result.getDocumentType(), normalizedType);

            // 5. Save full text to documents table
            log.info("\nSTEP 3: Saving full text to Supabase...");
            document.markAsCompleted(
                    result.getFullText(),
                    normalizedType,
                    result.getConfidenceScore());
            documentRepository.save(document);

            // 5. Save structured metadata to SQL
            log.info("\nSTEP 4: Saving structured metadata to SQL...");
            saveStructuredMetadata(
                    message.documentId(),
                    message.userEmail(),
                    normalizedType,
                    result.getStructuredFieldsJson());

            // 7. Embed prose summary in Pinecone
            String textToEmbed = result.getProseSummary();
            if (textToEmbed == null || textToEmbed.isBlank()) {
                log.warn("   No prose summary — falling back to full text");
                textToEmbed = result.getFullText();
            }

            if (textToEmbed != null && !textToEmbed.isBlank()) {
                log.info("\nSTEP 6: Embedding in Pinecone ({} chars)...", textToEmbed.length());
                pineconeService.storeDocument(
                        message.documentId().toString(),
                        message.userEmail(),
                        textToEmbed,
                        normalizedType,
                        result.getConfidenceScore());
            }

            // 7. Final status
            document.setProcessingStatus(Document.ProcessingStatus.COMPLETED);
            documentRepository.save(document);

            log.info("\n🎉 PIPELINE COMPLETE!");
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            log.info("   Full text → Supabase (documents.extracted_text)");
            log.info("   Structured fields → Supabase (document_metadata)");
            log.info("   Prose summary → Pinecone (vectors)");
            log.info("    Type: {}", result.getDocumentType());
            log.info("   Confidence: {}", String.format("%.1f%%", result.getConfidenceScore() * 100));
            log.info("   Ready for HYBRID SEARCH!");
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

        } catch (Exception e) {
            log.error("PIPELINE FAILED: {}", message.documentId());
            log.error("Error: {}", e.getMessage(), e);

            documentRepository.findById(message.documentId()).ifPresent(doc -> {
                doc.markAsFailed();
                documentRepository.save(doc);
            });
        }
    }

    /**
     * Parse structured_fields JSON and save each field as a DocumentMetadata row.
     */
    private void saveStructuredMetadata(java.util.UUID documentId, String userEmail,
            String documentType, String fieldsJson) {
        if (fieldsJson == null || fieldsJson.isBlank() || fieldsJson.equals("[]")) {
            log.warn("   No structured fields to save");
            return;
        }

        try {
            metadataRepository.deleteByDocumentId(documentId);

            List<Map<String, Object>> fields = objectMapper.readValue(
                    fieldsJson, new TypeReference<>() {
                    });

            int savedCount = 0;
            for (Map<String, Object> field : fields) {
                String fieldName = (String) field.get("field_name");
                String value = (String) field.get("value");
                String type = (String) field.get("type");

                Double confidence = 0.0;
                Object confObj = field.get("confidence");
                if (confObj instanceof Number)
                    confidence = ((Number) confObj).doubleValue();

                if (fieldName != null && value != null && !value.isBlank()) {
                    DocumentMetadata metadata = new DocumentMetadata(
                            documentId, userEmail, documentType,
                            fieldName, value, type, confidence);
                    metadataRepository.save(metadata);
                    savedCount++;
                    log.info("   {} = {} ({})", fieldName, value, type);
                }
            }
            log.info("   Saved {} fields to SQL", savedCount);

        } catch (Exception e) {
            log.error("   Failed to parse structured fields: {}", e.getMessage(), e);
        }
    }

    private String formatFileSize(Long bytes) {
        if (bytes == null)
            return "Unknown";
        if (bytes < 1024)
            return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
