package com.smartdocs.auth.service;

import com.smartdocs.auth.entity.User;
import com.smartdocs.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserService — registration, authentication, and user lookup.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User("test@smartdocs.com", "hashedPassword", "John", "Doe");
        testUser.setId(1L);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Registration Tests
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Test
    @DisplayName("Should register a new user successfully")
    void registerUser_shouldCreateNewUser() {
        when(userRepository.existsByEmail("new@smartdocs.com")).thenReturn(false);
        when(passwordEncoder.encode("rawPassword")).thenReturn("hashedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.registerUser("new@smartdocs.com", "rawPassword", "Jane", "Doe");

        assertNotNull(result);
        assertEquals("new@smartdocs.com", result.getEmail());
        assertEquals("hashedPassword", result.getPassword());
        assertEquals("Jane", result.getFirstName());
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw exception when registering duplicate email")
    void registerUser_shouldThrowOnDuplicateEmail() {
        when(userRepository.existsByEmail("test@smartdocs.com")).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.registerUser("test@smartdocs.com", "pass", "John", "Doe"));

        assertTrue(ex.getMessage().contains("already exists"));
        verify(userRepository, never()).save(any());
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Authentication Tests
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Test
    @DisplayName("Should authenticate user with correct credentials")
    void authenticateUser_shouldReturnUserOnValidCredentials() {
        when(userRepository.findByEmail("test@smartdocs.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("rawPassword", "hashedPassword")).thenReturn(true);

        User result = userService.authenticateUser("test@smartdocs.com", "rawPassword");

        assertNotNull(result);
        assertEquals("test@smartdocs.com", result.getEmail());
    }

    @Test
    @DisplayName("Should throw exception on invalid email")
    void authenticateUser_shouldThrowOnInvalidEmail() {
        when(userRepository.findByEmail("wrong@smartdocs.com")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> userService.authenticateUser("wrong@smartdocs.com", "password"));
    }

    @Test
    @DisplayName("Should throw exception on wrong password")
    void authenticateUser_shouldThrowOnWrongPassword() {
        when(userRepository.findByEmail("test@smartdocs.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongPassword", "hashedPassword")).thenReturn(false);

        assertThrows(RuntimeException.class,
                () -> userService.authenticateUser("test@smartdocs.com", "wrongPassword"));
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Lookup Tests
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Test
    @DisplayName("Should load user by username (email) for Spring Security")
    void loadUserByUsername_shouldReturnUserDetails() {
        when(userRepository.findByEmail("test@smartdocs.com")).thenReturn(Optional.of(testUser));

        var userDetails = userService.loadUserByUsername("test@smartdocs.com");

        assertEquals("test@smartdocs.com", userDetails.getUsername());
    }

    @Test
    @DisplayName("Should throw UsernameNotFoundException for unknown email")
    void loadUserByUsername_shouldThrowForUnknownEmail() {
        when(userRepository.findByEmail("unknown@smartdocs.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> userService.loadUserByUsername("unknown@smartdocs.com"));
    }

    @Test
    @DisplayName("Should find user by email")
    void findByEmail_shouldReturnOptionalUser() {
        when(userRepository.findByEmail("test@smartdocs.com")).thenReturn(Optional.of(testUser));

        Optional<User> result = userService.findByEmail("test@smartdocs.com");

        assertTrue(result.isPresent());
        assertEquals("John", result.get().getFirstName());
    }
}
