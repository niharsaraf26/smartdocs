package com.smartdocs.auth.controller;

import com.smartdocs.auth.dto.AuthResponse;
import com.smartdocs.auth.dto.LoginRequest;
import com.smartdocs.auth.dto.RegisterRequest;
import com.smartdocs.auth.entity.User;
import com.smartdocs.common.dto.ApiResponse;
import com.smartdocs.common.security.JwtService;
import com.smartdocs.auth.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtService jwtService;

    /**
     * REGISTRATION ENDPOINT: POST /auth/register
     *
     * Frontend Flow:
     * 1. User fills registration form
     * 2. Frontend sends POST /api/auth/register with user data
     * 3. API Gateway routes to this endpoint
     * 4. We validate, create user, generate JWT, return token
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Registration attempt for email: {}", request.email());

        User user = userService.registerUser(
                request.email(),
                request.password(),
                request.firstName(),
                request.lastName());

        String jwtToken = jwtService.generateToken(user);

        AuthResponse authResponse = new AuthResponse(
                jwtToken,
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                "Registration successful! Welcome to SmartDocs!");

        log.info("Registration successful for: {}", request.email());
        return ResponseEntity.ok(ApiResponse.success(authResponse, "Registration successful"));
    }

    /**
     * LOGIN ENDPOINT: POST /auth/login
     *
     * Frontend Flow:
     * 1. User enters email/password
     * 2. Frontend sends POST /api/auth/login with credentials
     * 3. API Gateway routes to this endpoint
     * 4. We validate credentials, generate JWT, return token
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login attempt for email: {}", request.email());

        User user = userService.authenticateUser(request.email(), request.password());
        String jwtToken = jwtService.generateToken(user);

        AuthResponse authResponse = new AuthResponse(
                jwtToken,
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                "Login successful! Welcome back to SmartDocs!");

        log.info("Login successful for: {}", request.email());
        return ResponseEntity.ok(ApiResponse.success(authResponse, "Login successful"));
    }

    /**
     * USER PROFILE ENDPOINT: GET /auth/me
     * Protected endpoint — requires JWT token
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AuthResponse>> getProfile() {
        String email = org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication().getName();

        User user = userService.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        AuthResponse authResponse = new AuthResponse(
                null,
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                "Profile retrieved successfully");

        return ResponseEntity.ok(ApiResponse.success(authResponse, "Profile retrieved successfully"));
    }
}
