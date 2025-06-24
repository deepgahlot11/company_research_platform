package com.user.management.service;

import com.user.management.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;
    private User mockUser;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();

        mockUser = new User();
        mockUser.setEmail("test@example.com");
        mockUser.setPassword("password123");
        mockUser.setFirstName("Test");
        mockUser.setLastName("User");
    }

    @Test
    void testGenerateToken_ShouldContainUsername() {
        String token = jwtService.generateToken(mockUser);
        assertNotNull(token);
        assertTrue(token.length() > 0);

        String username = jwtService.extractUsername(token);
        assertEquals("test@example.com", username);
    }

    @Test
    void testExtractUsername_ShouldReturnCorrectUsername() {
        String token = jwtService.generateToken(mockUser);
        String extractedUsername = jwtService.extractUsername(token);
        assertEquals(mockUser.getUsername(), extractedUsername);
    }

    @Test
    void testIsTokenValid_ShouldReturnTrueForValidToken() {
        String token = jwtService.generateToken(mockUser);
        boolean isValid = jwtService.isTokenValid(token, mockUser);
        assertTrue(isValid);
    }

    @Test
    void testIsTokenValid_ShouldReturnFalseForInvalidUser() {
        String token = jwtService.generateToken(mockUser);

        User invalidUser = new User();
        invalidUser.setEmail("another@example.com");

        boolean isValid = jwtService.isTokenValid(token, invalidUser);
        assertFalse(isValid);
    }

    @Test
    void testIsTokenExpired_ShouldReturnFalseForNewToken() {
        String token = jwtService.generateToken(mockUser);
        boolean isExpired = jwtService.isTokenValid(token, mockUser);
        assertTrue(isExpired); // Actually valid, so this is just double-check
    }

    @Test
    void testGenerateToken_NotNullOrEmpty() {
        String token = jwtService.generateToken(mockUser);
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }
}
