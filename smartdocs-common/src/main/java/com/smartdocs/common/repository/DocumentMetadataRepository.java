package com.smartdocs.common.repository;

import com.smartdocs.common.entity.DocumentMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentMetadataRepository extends JpaRepository<DocumentMetadata, UUID> {

    // All metadata fields for a specific document
    List<DocumentMetadata> findByDocumentId(UUID documentId);

    // All metadata for a user (for dashboard display)
    List<DocumentMetadata> findByUserEmail(String userEmail);

    // Cross-document lookup: find a specific field across ALL user's documents
    // e.g. find "person_name" across Aadhar, Marksheet, PAN
    List<DocumentMetadata> findByUserEmailAndFieldNameIgnoreCase(String userEmail, String fieldName);

    // Fuzzy field name search (e.g. searching "name" matches "person_name",
    // "mothers_name")
    List<DocumentMetadata> findByUserEmailAndFieldNameContainingIgnoreCase(String userEmail, String fieldName);

    // Search by field value across all documents
    @Query("SELECT dm FROM DocumentMetadata dm WHERE dm.userEmail = :email " +
            "AND LOWER(dm.fieldValue) LIKE LOWER(CONCAT('%', :value, '%'))")
    List<DocumentMetadata> searchByFieldValue(@Param("email") String email, @Param("value") String value);

    // All metadata for a specific document type
    List<DocumentMetadata> findByUserEmailAndDocumentTypeIgnoreCase(String userEmail, String documentType);

    // Delete all metadata for a document (when document is deleted/re-uploaded)
    void deleteByDocumentId(UUID documentId);
}
