package com.smartdocs.documents.service;

import com.smartdocs.common.entity.Document;
import com.smartdocs.common.repository.DocumentRepository;
import com.smartdocs.common.service.SupabaseStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DocumentService — file validation, upload, retrieval, and download URL generation.
 */
@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private SupabaseStorageService storageService;

    @Mock
    private DocumentProcessingPublisher publisher;

    @InjectMocks
    private DocumentService documentService;

    private static final String USER_EMAIL = "test@smartdocs.com";

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Upload / Validation Tests
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Test
    @DisplayName("Should upload a valid PDF file successfully")
    void uploadDocument_shouldSucceedForValidPdf() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "PDF content".getBytes());

        when(storageService.uploadFile(any(), eq(USER_EMAIL))).thenReturn("path/to/file.pdf");
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> {
            Document doc = inv.getArgument(0);
            doc.setId(UUID.randomUUID());
            return doc;
        });

        Document result = documentService.uploadDocument(file, USER_EMAIL);

        assertNotNull(result);
        assertEquals("test.pdf", result.getOriginalFilename());
        assertEquals(USER_EMAIL, result.getUserEmail());
        verify(publisher).queueForProcessing(any(Document.class));
    }

    @Test
    @DisplayName("Should reject an empty file")
    void uploadDocument_shouldRejectEmptyFile() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.pdf", "application/pdf", new byte[0]);

        assertThrows(IllegalArgumentException.class,
                () -> documentService.uploadDocument(emptyFile, USER_EMAIL));
    }

    @Test
    @DisplayName("Should reject file exceeding 10MB limit")
    void uploadDocument_shouldRejectOversizedFile() {
        byte[] largeContent = new byte[11 * 1024 * 1024]; // 11MB
        MockMultipartFile largeFile = new MockMultipartFile(
                "file", "large.pdf", "application/pdf", largeContent);

        assertThrows(IllegalArgumentException.class,
                () -> documentService.uploadDocument(largeFile, USER_EMAIL));
    }

    @Test
    @DisplayName("Should reject unsupported file type")
    void uploadDocument_shouldRejectUnsupportedType() {
        MockMultipartFile exeFile = new MockMultipartFile(
                "file", "malware.exe", "application/x-msdownload", "content".getBytes());

        assertThrows(IllegalArgumentException.class,
                () -> documentService.uploadDocument(exeFile, USER_EMAIL));
    }

    @Test
    @DisplayName("Should accept JPEG image uploads")
    void uploadDocument_shouldAcceptJpeg() throws Exception {
        MockMultipartFile jpegFile = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "jpeg data".getBytes());

        when(storageService.uploadFile(any(), eq(USER_EMAIL))).thenReturn("path/to/photo.jpg");
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> {
            Document doc = inv.getArgument(0);
            doc.setId(UUID.randomUUID());
            return doc;
        });

        Document result = documentService.uploadDocument(jpegFile, USER_EMAIL);
        assertNotNull(result);
        assertEquals("photo.jpg", result.getOriginalFilename());
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Retrieval Tests
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Test
    @DisplayName("Should return user documents sorted by date")
    void getUserDocuments_shouldReturnDocumentsList() {
        Document doc1 = new Document("file1.pdf", "stored1.pdf", 1024L, "application/pdf", USER_EMAIL, "path1");
        Document doc2 = new Document("file2.pdf", "stored2.pdf", 2048L, "application/pdf", USER_EMAIL, "path2");
        when(documentRepository.findByUserEmailOrderByCreatedAtDesc(USER_EMAIL))
                .thenReturn(List.of(doc1, doc2));

        List<Document> results = documentService.getUserDocuments(USER_EMAIL);

        assertEquals(2, results.size());
        verify(documentRepository).findByUserEmailOrderByCreatedAtDesc(USER_EMAIL);
    }

    @Test
    @DisplayName("Should return specific document with ownership check")
    void getDocument_shouldReturnDocumentForOwner() {
        UUID docId = UUID.randomUUID();
        Document doc = new Document("file.pdf", "stored.pdf", 1024L, "application/pdf", USER_EMAIL, "path");
        when(documentRepository.findByIdAndUserEmail(docId, USER_EMAIL)).thenReturn(Optional.of(doc));

        Document result = documentService.getDocument(docId, USER_EMAIL);

        assertNotNull(result);
        assertEquals("file.pdf", result.getOriginalFilename());
    }

    @Test
    @DisplayName("Should throw when document not found or access denied")
    void getDocument_shouldThrowWhenNotFoundOrWrongUser() {
        UUID docId = UUID.randomUUID();
        when(documentRepository.findByIdAndUserEmail(docId, USER_EMAIL)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> documentService.getDocument(docId, USER_EMAIL));
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Download URL Tests
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Test
    @DisplayName("Should generate signed download URL")
    void getDownloadUrl_shouldReturnSignedUrl() {
        UUID docId = UUID.randomUUID();
        Document doc = new Document("file.pdf", "stored.pdf", 1024L, "application/pdf", USER_EMAIL, "path/to/file");
        when(documentRepository.findByIdAndUserEmail(docId, USER_EMAIL)).thenReturn(Optional.of(doc));
        when(storageService.getSignedDownloadUrl("path/to/file", 3600)).thenReturn("https://signed-url.com/file.pdf");

        String url = documentService.getDownloadUrl(docId, USER_EMAIL);

        assertEquals("https://signed-url.com/file.pdf", url);
        verify(storageService).getSignedDownloadUrl("path/to/file", 3600);
    }
}
