package com.smartdocs.common.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "documents", indexes = {
        @Index(name = "idx_user_email", columnList = "user_email"),
        @Index(name = "idx_processing_status", columnList = "processing_status"),
        @Index(name = "idx_created_at", columnList = "created_at")
})

public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // File Information
    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename; // User's original filename: "my-aadhar.pdf"

    @Column(name = "stored_filename", nullable = false, unique = true, length = 255)
    private String storedFilename; // UUID-based secure filename: "a1b2c3d4-e5f6.pdf"

    @Column(name = "file_size", nullable = false)
    private Long fileSize; // Size in bytes

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType; // MIME type: "application/pdf"

    // Storage Information
    @Column(name = "storage_url", length = 500)
    private String storageUrl; // URL in cloud storage (Supabase)

    @Column(name = "storage_path", length = 500)
    private String storagePath; // Path in storage: "user123/documents/filename.pdf"

    // Security & Ownership
    @Column(name = "user_email", nullable = false, length = 255)
    private String userEmail; // Owner of this document

    // Processing Pipeline
    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false)
    private ProcessingStatus processingStatus = ProcessingStatus.UPLOADED;

    // AI Processing Results
    @Column(name = "extracted_text", columnDefinition = "TEXT")
    private String extractedText; // Full text extracted by AI

    @Column(name = "document_type", length = 50)
    private String documentType; // aadhar, passport, bank_statement, etc.

    @Column(name = "confidence_score")
    private Double confidenceScore; // AI confidence in document type (0.0-1.0)

    // Timestamps
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    // Processing Status Enum
    public enum ProcessingStatus {
        UPLOADED, // File uploaded successfully, ready for processing
        QUEUED, // Queued for AI processing
        PROCESSING, // Currently being processed by AI
        COMPLETED, // AI processing completed successfully
        FAILED, // Processing failed (will retry)
        ARCHIVED // User archived/soft deleted
    }

    // Constructors
    public Document() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Document(String originalFilename, String storedFilename, Long fileSize,
            String contentType, String userEmail, String storagePath) {
        this();
        this.originalFilename = originalFilename;
        this.storedFilename = storedFilename;
        this.fileSize = fileSize;
        this.contentType = contentType;
        this.userEmail = userEmail;
        this.storagePath = storagePath;
    }

    // JPA Lifecycle Callbacks
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Business Logic Methods
    public void markAsQueued() {
        this.processingStatus = ProcessingStatus.QUEUED;
        this.updatedAt = LocalDateTime.now();
    }

    public void markAsProcessing() {
        this.processingStatus = ProcessingStatus.PROCESSING;
        this.updatedAt = LocalDateTime.now();
    }

    public void markAsCompleted(String extractedText, String documentType, Double confidenceScore) {
        this.processingStatus = ProcessingStatus.COMPLETED;
        this.extractedText = extractedText;
        this.documentType = documentType;
        this.confidenceScore = confidenceScore;
        this.processedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void markAsFailed() {
        this.processingStatus = ProcessingStatus.FAILED;
        this.updatedAt = LocalDateTime.now();
    }

    // Helper Methods
    public boolean isProcessed() {
        return ProcessingStatus.COMPLETED.equals(this.processingStatus);
    }

    public boolean canBeReprocessed() {
        return ProcessingStatus.FAILED.equals(this.processingStatus) ||
                ProcessingStatus.UPLOADED.equals(this.processingStatus);
    }

    public String getFileExtension() {
        if (originalFilename != null && originalFilename.contains(".")) {
            return originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        return "";
    }

    // Getters and Setters (Full implementation)
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getStoredFilename() {
        return storedFilename;
    }

    public void setStoredFilename(String storedFilename) {
        this.storedFilename = storedFilename;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getStorageUrl() {
        return storageUrl;
    }

    public void setStorageUrl(String storageUrl) {
        this.storageUrl = storageUrl;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public ProcessingStatus getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(ProcessingStatus processingStatus) {
        this.processingStatus = processingStatus;
    }

    public String getExtractedText() {
        return extractedText;
    }

    public void setExtractedText(String extractedText) {
        this.extractedText = extractedText;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public Double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(Double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }

    @Override
    public String toString() {
        return "Document{" +
                "id=" + id +
                ", originalFilename='" + originalFilename + '\'' +
                ", userEmail='" + userEmail + '\'' +
                ", processingStatus=" + processingStatus +
                ", createdAt=" + createdAt +
                '}';
    }
}
