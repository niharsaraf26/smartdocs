package com.smartdocs.common.message;

import com.smartdocs.common.entity.Document;

import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentProcessingMessage(
        UUID documentId,
        String userEmail,
        String originalFilename,
        String storagePath,
        String contentType,
        Long fileSize,
        LocalDateTime createdAt
) {
    public static DocumentProcessingMessage from(Document document) {
        return new DocumentProcessingMessage(
                document.getId(),
                document.getUserEmail(),
                document.getOriginalFilename(),
                document.getStoragePath(),
                document.getContentType(),
                document.getFileSize(),
                document.getCreatedAt()
        );
    }
}
