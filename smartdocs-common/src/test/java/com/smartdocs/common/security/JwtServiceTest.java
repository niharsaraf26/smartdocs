package com.smartdocs.common.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.lang.reflect.Field;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JwtService — token generation, validation, and claim
 * extraction.
 */
class JwtServiceTest {

    private JwtService jwtService;

    // A valid Base64-encoded 256-bit key for HS256
    private static final String TEST_SECRET = "dGVzdC1zZWNyZXQta2V5LWZvci1zbWFydGRvY3MtdW5pdC10ZXN0aW5n";

    @BeforeEach
    void setUp() throws Exception {
        jwtService = new JwtService();

        // Inject the secret and expiration via reflection (since @Value won't work in
        // plain unit tests)
        Field secretField = JwtService.class.getDeclaredField("jwtSecret");
        secretField.setAccessible(true);
        secretField.set(jwtService, TEST_SECRET);

        Field expirationField = JwtService.class.getDeclaredField("jwtExpiration");
        expirationField.setAccessible(true);
        expirationField.set(jwtService, 86400000L); // 24 hours
    }

    private UserDetails createTestUser(String email) {
        return new User(email, "password", Collections.emptyList());
    }

    @Test
    @DisplayName("Should generate a non-null JWT token")
    void generateToken_shouldReturnNonNullToken() {
        UserDetails user = createTestUser("test@smartdocs.com");
        String token = jwtService.generateToken(user);

        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    @DisplayName("Should extract correct username from generated token")
    void extractUsername_shouldReturnCorrectEmail() {
        String email = "test@smartdocs.com";
        UserDetails user = createTestUser(email);
        String token = jwtService.generateToken(user);

        String extractedUsername = jwtService.extractUsername(token);
        assertEquals(email, extractedUsername);
    }

    @Test
    @DisplayName("Should validate a correctly generated token")
    void validateToken_shouldReturnTrueForValidToken() {
        UserDetails user = createTestUser("test@smartdocs.com");
        String token = jwtService.generateToken(user);

        assertTrue(jwtService.validateToken(token));
    }

    @Test
    @DisplayName("Should reject a token with invalid signature")
    void validateToken_shouldReturnFalseForTamperedToken() {
        UserDetails user = createTestUser("test@smartdocs.com");
        String token = jwtService.generateToken(user);

        // Tamper with the token by changing the last character
        String tamperedToken = token.substring(0, token.length() - 1) + "X";

        assertFalse(jwtService.validateToken(tamperedToken));
    }

    @Test
    @DisplayName("Should reject a malformed token")
    void validateToken_shouldReturnFalseForMalformedToken() {
        assertFalse(jwtService.validateToken("not.a.valid.jwt"));
    }

    @Test
    @DisplayName("Should reject an expired token")
    void validateToken_shouldReturnFalseForExpiredToken() throws Exception {
        // Set expiration to 0ms (immediately expired)
        Field expirationField = JwtService.class.getDeclaredField("jwtExpiration");
        expirationField.setAccessible(true);
        expirationField.set(jwtService, 0L);

        UserDetails user = createTestUser("test@smartdocs.com");
        String token = jwtService.generateToken(user);

        // Small delay to ensure expiration
        Thread.sleep(10);

        assertFalse(jwtService.validateToken(token));
    }

    @Test
    @DisplayName("Should generate different tokens for different users")
    void generateToken_shouldCreateUniqueTokensPerUser() {
        String token1 = jwtService.generateToken(createTestUser("user1@smartdocs.com"));
        String token2 = jwtService.generateToken(createTestUser("user2@smartdocs.com"));

        assertNotEquals(token1, token2);
    }
}
