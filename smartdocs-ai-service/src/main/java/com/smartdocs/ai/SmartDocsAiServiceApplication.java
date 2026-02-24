package com.smartdocs.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableDiscoveryClient
@EnableAsync
@ComponentScan(basePackages = {
        "com.smartdocs.ai",
        "com.smartdocs.common",
        "com.smartdocs.common.security"
})
@EntityScan(basePackages = "com.smartdocs.common.entity")
@EnableJpaRepositories(basePackages = "com.smartdocs.common.repository")
public class SmartDocsAiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartDocsAiServiceApplication.class, args);
    }

}
