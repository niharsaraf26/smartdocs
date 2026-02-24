package com.smartdocs.common.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity-Attribute-Value (EAV) table for structured document metadata.
 *
 * Each row represents one extracted field from a document.
 * This design supports ANY document type without schema changes:
 * - Identity docs → person_name, dob, id_number
 * - Invoices/bills → provider, amount_due, billing_period
 * - Certificates → institution, issue_date, grade
 */
@Entity
@Table(name = "document_metadata", indexes = {
        @Index(name = "idx_dm_document_id", columnList = "document_id"),
        @Index(name = "idx_dm_user_email", columnList = "user_email"),
        @Index(name = "idx_dm_field_name", columnList = "user_email, field_name")
})
public class DocumentMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId; // FK → documents.id

    @Column(name = "user_email", nullable = false, length = 255)
    private String userEmail;

    @Column(name = "document_type", length = 100)
    private String documentType; // "Educational Document", "Aadhar Card", "Invoice"

    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName; // e.g. "person_name", "mothers_name", "amount_due"

    @Column(name = "field_value", columnDefinition = "TEXT")
    private String fieldValue; // e.g. "Nihar Saraf", "₹999.00"

    @Column(name = "field_type", length = 50)
    private String fieldType; // PERSON, DATE, ID_NUMBER, AMOUNT, ORGANIZATION, LOCATION, TEXT

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // Constructors
    public DocumentMetadata() {
        this.createdAt = LocalDateTime.now();
    }

    public DocumentMetadata(UUID documentId, String userEmail, String documentType,
            String fieldName, String fieldValue, String fieldType, Double confidence) {
        this();
        this.documentId = documentId;
        this.userEmail = userEmail;
        this.documentType = documentType;
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
        this.fieldType = fieldType;
        this.confidence = confidence;
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public void setDocumentId(UUID documentId) {
        this.documentId = documentId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getFieldValue() {
        return fieldValue;
    }

    public void setFieldValue(String fieldValue) {
        this.fieldValue = fieldValue;
    }

    public String getFieldType() {
        return fieldType;
    }

    public void setFieldType(String fieldType) {
        this.fieldType = fieldType;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return String.format("DocumentMetadata{doc=%s, field='%s', value='%s', type='%s'}",
                documentId, fieldName, fieldValue, fieldType);
    }
}
