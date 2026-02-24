package com.smartdocs.documents.controller;

import com.smartdocs.common.dto.ApiResponse;
import com.smartdocs.common.entity.Document;
import com.smartdocs.documents.service.DocumentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/documents")
public class DocumentController {

    @Autowired
    private DocumentService documentService;

    /**
     * Upload document endpoint
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadDocument(
            @RequestParam("file") MultipartFile file) throws IOException {

        String authenticatedUser = getCurrentUserEmail();
        Document document = documentService.uploadDocument(file, authenticatedUser);

        Map<String, Object> data = Map.of(
                "documentId", document.getId().toString(),
                "filename", document.getOriginalFilename(),
                "size", document.getFileSize(),
                "uploadedAt", document.getCreatedAt(),
                "owner", authenticatedUser
        );

        return ResponseEntity.ok(ApiResponse.success(data, "File uploaded successfully"));
    }

    /**
     * Get user's documents
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUserDocuments() {
        String userEmail = getCurrentUserEmail();
        List<Document> documents = documentService.getUserDocuments(userEmail);

        Map<String, Object> data = Map.of(
                "documents", documents,
                "totalCount", documents.size()
        );

        return ResponseEntity.ok(ApiResponse.success(data, "Documents retrieved successfully"));
    }

    /**
     * Get specific document
     */
    @GetMapping("/{documentId}")
    public ResponseEntity<ApiResponse<Document>> getDocument(
            @PathVariable(name = "documentId") UUID documentId) {

        String userEmail = getCurrentUserEmail();
        Document document = documentService.getDocument(documentId, userEmail);

        return ResponseEntity.ok(ApiResponse.success(document, "Document retrieved successfully"));
    }

    /**
     * Get secure download URL for document
     */
    @GetMapping("/{documentId}/download-url")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDownloadUrl(
            @PathVariable(name = "documentId") UUID documentId) {

        String userEmail = getCurrentUserEmail();
        Document document = documentService.getDocument(documentId, userEmail);
        String downloadUrl = documentService.getDownloadUrl(documentId, userEmail);

        Map<String, Object> data = Map.of(
                "downloadUrl", downloadUrl,
                "filename", document.getOriginalFilename(),
                "expiresIn", "1 hour"
        );

        return ResponseEntity.ok(ApiResponse.success(data, "Download URL generated successfully"));
    }

    /**
     * Extract authenticated user email from security context
     */
    private String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("User not authenticated");
        }

        return authentication.getName();
    }
}
