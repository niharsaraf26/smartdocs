package com.smartdocs.ai.config;

import com.smartdocs.ai.service.PineconeVectorStoreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Custom Actuator HealthIndicator for Pinecone connectivity.
 * Automatically included in GET /actuator/health response.
 */
@Slf4j
@Component
public class PineconeHealthIndicator implements HealthIndicator {

    @Autowired
    private PineconeVectorStoreService pineconeService;

    @Override
    public Health health() {
        try {
            boolean connected = pineconeService.testConnection();
            if (connected) {
                return Health.up()
                        .withDetails(pineconeService.getStats())
                        .build();
            } else {
                return Health.down()
                        .withDetail("error", "Unable to connect to Pinecone")
                        .build();
            }
        } catch (Exception e) {
            log.error("Pinecone health check failed: {}", e.getMessage());
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
