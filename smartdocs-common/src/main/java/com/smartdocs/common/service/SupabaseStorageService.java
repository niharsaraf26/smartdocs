package com.smartdocs.common.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class SupabaseStorageService {

    private final WebClient webClient;

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.service-key}")
    private String serviceKey;

    @Value("${supabase.bucket}")
    private String bucketName;

    public SupabaseStorageService() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
                .build();
    }

    /**
     * Upload file to Supabase Storage
     * Returns the object path for database storage
     */
    public String uploadFile(MultipartFile file, String userEmail) throws IOException {

        // Generate secure file path: userEmail/uuid-filename.ext
        String fileName = UUID.randomUUID().toString() + getFileExtension(file.getOriginalFilename());
        // String fileName = file.getOriginalFilename();
        String objectPath = userEmail + "/" + fileName;

        // Upload to Supabase Storage via REST API
        String uploadResponse = webClient
                .post()
                .uri(supabaseUrl + "/storage/v1/object/" + bucketName + "/" + objectPath)
                .header("Authorization", "Bearer " + serviceKey)
                .header("Content-Type", file.getContentType())
                .bodyValue(file.getBytes())
                .retrieve()
                .bodyToMono(String.class)
                .block();

        log.info("File uploaded to Supabase Storage: {}", objectPath);
        return objectPath;
    }

    /**
     * Generate signed URL for secure file download
     */
    public String getSignedDownloadUrl(String objectPath, int expiresInSeconds) {

        Map<String, Object> requestBody = Map.of(
                "expiresIn", expiresInSeconds);

        Map<String, String> response = webClient
                .post()
                .uri(supabaseUrl + "/storage/v1/object/sign/" + bucketName + "/" + objectPath)
                .header("Authorization", "Bearer " + serviceKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .cast(Map.class)
                .block();

        return supabaseUrl + "/storage/v1" + response.get("signedURL");
    }

    /**
     * Download file content directly (for processing)
     */
    public byte[] downloadFile(String objectPath) {

        return webClient
                .get()
                .uri(supabaseUrl + "/storage/v1/object/" + bucketName + "/" + objectPath)
                .header("Authorization", "Bearer " + serviceKey)
                .retrieve()
                .bodyToMono(byte[].class)
                .block();
    }

    /**
     * Delete file from storage
     */
    public boolean deleteFile(String objectPath) {
        try {
            webClient
                    .delete()
                    .uri(supabaseUrl + "/storage/v1/object/" + bucketName + "/" + objectPath)
                    .header("Authorization", "Bearer " + serviceKey)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return true;
        } catch (Exception e) {
            log.error("Failed to delete file: {}", objectPath, e);
            return false;
        }
    }

    private String getFileExtension(String filename) {
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf("."));
        }
        return "";
    }
}
