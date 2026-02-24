package com.smartdocs.apigateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootApplication
@EnableDiscoveryClient
public class ApiGatewayApplication {

        public static void main(String[] args) {
                SpringApplication.run(ApiGatewayApplication.class, args);
                log.info("SmartDocs API Gateway with service routing is running!");
                log.info("Gateway Dashboard: http://localhost:8080");
        }

        /**
         * MAIN ROUTING CONFIGURATION
         * Define how incoming requests are routed to microservices
         */
        @Bean
        public RouteLocator smartDocsRoutes(RouteLocatorBuilder builder) {
                return builder.routes()

                                // AUTH SERVICE ROUTING
                                // Routes /api/auth/** to AUTH-SERVICE discovered via Eureka
                                .route("auth-service", r -> r.path("/api/auth/**")
                                                .filters(f -> f.stripPrefix(1)) // Remove /api from path
                                                .uri("lb://AUTH-SERVICE")) // Load balance to AUTH-SERVICE

                                // FUTURE DOCUMENT SERVICE ROUTING
                                .route("document-service", r -> r.path("/api/documents/**")
                                                .filters(f -> f.stripPrefix(1))
                                                .uri("lb://DOCUMENT-SERVICE"))

                                // AI SERVICE ROUTING
                                .route("ai-service", r -> r.path("/api/ai/**")
                                                .filters(f -> f.stripPrefix(1))
                                                .uri("lb://SMARTDOCS-AI-SERVICE"))

                                // HEALTH CHECK ROUTING (keep on gateway)
                                .route("gateway-health", r -> r.path("/health")
                                                .uri("http://localhost:8080"))

                                .build();
        }
}

/**
 * Gateway Health Controller
 * Provides information about routing and registered services
 */
@RestController
class GatewayHealthController {

        @GetMapping("/health")
        public String health() {
                return """
                                SmartDocs API Gateway is running!
                                Available Routes:
                                • /api/auth/**       → AUTH-SERVICE
                                • /api/documents/**  → DOCUMENT-SERVICE
                                • /api/ai/**         → SMARTDOCS-AI-SERVICE
                                Service Discovery: Eureka enabled
                                Load Balancing: Active
                                Health: /actuator/health (per-service)
                                """;
        }
}
