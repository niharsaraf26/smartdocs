package com.smartdocs.documents.service;

import com.smartdocs.common.entity.Document;
import com.smartdocs.common.repository.DocumentRepository;
import com.smartdocs.common.service.SupabaseStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class DocumentService {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private SupabaseStorageService storageService;

    @Autowired
    private DocumentProcessingPublisher publisher;

    // Allowed file types (we'll expand this later)
    private static final List<String> ALLOWED_TYPES = Arrays.asList(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "text/plain"
    );

    // Max file size (10MB)
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;


    public Document uploadDocument(MultipartFile file, String userEmail) throws IOException {

        // 1. Basic validation
        validateFile(file);

        String storagePath = storageService.uploadFile(file, userEmail);

        // 2. Create document metadata
        String storedFilename = generateStoredFilename(file.getOriginalFilename());

        Document document = new Document(
                file.getOriginalFilename(),
                generateStoredFilename(file.getOriginalFilename()),
                file.getSize(),
                file.getContentType(),
                userEmail,
                storagePath  // Supabase object path
        );

        // 3. Save to database
        Document saved = documentRepository.save(document);

        publisher.queueForProcessing(saved);

        log.info("Document uploaded: {} for user: {}", saved.getOriginalFilename(), userEmail);

        return saved;
    }

    /**
     * Get secure download URL (doesn't download file, returns URL)
     */
    public String getDownloadUrl(UUID documentId, String userEmail) {
        Document document = getDocument(documentId, userEmail);

        // Generate 1-hour signed URL for security
        return storageService.getSignedDownloadUrl(document.getStoragePath(), 3600);
    }

    /**
     * Get user's documents
     */
    public List<Document> getUserDocuments(String userEmail) {
        return documentRepository.findByUserEmailOrderByCreatedAtDesc(userEmail);
    }

    /**
     * Get specific document (with security check)
     */
    public Document getDocument(UUID documentId, String userEmail) {
        return documentRepository.findByIdAndUserEmail(documentId, userEmail)
                .orElseThrow(() -> new RuntimeException("Document not found or access denied"));
    }

    /**
     * Basic file validation
     */
    private void validateFile(MultipartFile file) {
        // Check if file is empty
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Please select a file to upload");
        }

        // Check file size
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds limit of 10MB");
        }

        // Check file type
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("File type not allowed. Supported: PDF, JPEG, PNG, TXT");
        }

        // Check filename
        String filename = file.getOriginalFilename();
        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid filename");
        }
    }

    /**
     * Generate secure stored filename
     */
    private String generateStoredFilename(String originalFilename) {
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        return UUID.randomUUID().toString() + extension;
    }


}
