package com.smartdocs.common.repository;

import com.smartdocs.common.entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    /**
     * CORE METHODS - Only what we need for file upload functionality
     */

    // Security: Find document only if user owns it
    Optional<Document> findByIdAndUserEmail(UUID id, String userEmail);

    // List user's documents (for document listing page)
    List<Document> findByUserEmailOrderByCreatedAtDesc(String userEmail);

    // Paginated version (when user has many documents)
    Page<Document> findByUserEmailOrderByCreatedAtDesc(String userEmail, Pageable pageable);

    // Count user's documents (for UI display)
    long countByUserEmail(String userEmail);

    // CROSS_DOCUMENT: Fetch all completed docs for a user filtered by document
    // types
    // Used when the router identifies relevant document categories (e.g.,
    // "invoice", "receipt")
    List<Document> findByUserEmailAndProcessingStatusAndDocumentTypeIn(
            String userEmail,
            com.smartdocs.common.entity.Document.ProcessingStatus status,
            List<String> documentTypes);

    // CROSS_DOCUMENT fallback: Fetch ALL completed docs for a user (when no type
    // filter given)
    List<Document> findByUserEmailAndProcessingStatus(
            String userEmail,
            com.smartdocs.common.entity.Document.ProcessingStatus status);
}
