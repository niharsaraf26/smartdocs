package com.smartdocs.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import com.smartdocs.common.service.SupabaseStorageService;

@SpringBootApplication(scanBasePackages = { "com.smartdocs.auth", "com.smartdocs.common" })
@ComponentScan(basePackages = { "com.smartdocs.auth",
        "com.smartdocs.common" }, excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SupabaseStorageService.class))
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }

}
