package com.smartdocs.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Registration API Contract using Java Record
 * Immutable, concise, and thread-safe
 */
public record RegisterRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email should be valid")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 6, message = "Password must be at least 6 characters")
        String password,

        @NotBlank(message = "First name is required")
        String firstName,

        @NotBlank(message = "Last name is required")
        String lastName
) {
    // Optional: Custom validation or business logic
    public RegisterRequest {
        // Compact constructor - runs before field assignment
        if (email != null) {
            email = email.toLowerCase().trim(); // Normalize email
        }
    }
}
