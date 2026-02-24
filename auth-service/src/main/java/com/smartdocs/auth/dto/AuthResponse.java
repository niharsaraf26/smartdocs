package com.smartdocs.auth.dto;

public record AuthResponse(
        String token,
        String type,       // Default "Bearer"
        String email,
        String firstName,
        String lastName,
        String message
) {
    // Constructor with defaults
    public AuthResponse(String token, String email, String firstName, String lastName, String message) {
        this(token, "Bearer", email, firstName, lastName, message);
    }
}
