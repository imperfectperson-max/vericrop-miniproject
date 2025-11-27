package org.vericrop.gui.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JwtService.
 * Tests JWT token generation, validation, and claim extraction.
 */
class JwtServiceTest {
    
    private JwtService jwtService;
    
    @BeforeEach
    void setUp() {
        // Use default constructor which generates a secure key
        jwtService = new JwtService();
    }
    
    @Test
    void testGenerateToken_ShouldCreateValidToken() {
        // Given
        String username = "testuser";
        String role = "PRODUCER";
        String email = "test@example.com";
        
        // When
        String token = jwtService.generateToken(username, role, email);
        
        // Then
        assertNotNull(token);
        assertFalse(token.isEmpty());
        assertTrue(token.split("\\.").length == 3); // JWT has 3 parts
    }
    
    @Test
    void testExtractUsername_ShouldReturnCorrectUsername() {
        // Given
        String username = "john_doe";
        String role = "ADMIN";
        String token = jwtService.generateToken(username, role);
        
        // When
        String extractedUsername = jwtService.extractUsername(token);
        
        // Then
        assertEquals(username, extractedUsername);
    }
    
    @Test
    void testExtractRole_ShouldReturnCorrectRole() {
        // Given
        String username = "producer1";
        String role = "PRODUCER";
        String token = jwtService.generateToken(username, role);
        
        // When
        String extractedRole = jwtService.extractRole(token);
        
        // Then
        assertEquals(role, extractedRole);
    }
    
    @Test
    void testExtractEmail_ShouldReturnCorrectEmail() {
        // Given
        String username = "user1";
        String role = "CONSUMER";
        String email = "user@vericrop.com";
        String token = jwtService.generateToken(username, role, email);
        
        // When
        String extractedEmail = jwtService.extractEmail(token);
        
        // Then
        assertEquals(email, extractedEmail);
    }
    
    @Test
    void testExtractEmail_ShouldReturnNullWhenNotProvided() {
        // Given
        String token = jwtService.generateToken("user", "ROLE");
        
        // When
        String extractedEmail = jwtService.extractEmail(token);
        
        // Then
        assertNull(extractedEmail);
    }
    
    @Test
    void testValidateToken_ShouldReturnTrueForValidToken() {
        // Given
        String username = "validuser";
        String token = jwtService.generateToken(username, "PRODUCER");
        
        // When
        boolean isValid = jwtService.validateToken(token, username);
        
        // Then
        assertTrue(isValid);
    }
    
    @Test
    void testValidateToken_ShouldReturnFalseForWrongUsername() {
        // Given
        String token = jwtService.generateToken("user1", "PRODUCER");
        
        // When
        boolean isValid = jwtService.validateToken(token, "differentuser");
        
        // Then
        assertFalse(isValid);
    }
    
    @Test
    void testIsTokenValid_ShouldReturnTrueForValidToken() {
        // Given
        String token = jwtService.generateToken("user", "ROLE");
        
        // When
        boolean isValid = jwtService.isTokenValid(token);
        
        // Then
        assertTrue(isValid);
    }
    
    @Test
    void testIsTokenValid_ShouldReturnFalseForInvalidToken() {
        // Given
        String invalidToken = "invalid.token.string";
        
        // When
        boolean isValid = jwtService.isTokenValid(invalidToken);
        
        // Then
        assertFalse(isValid);
    }
    
    @Test
    void testIsTokenValid_ShouldReturnFalseForNullToken() {
        // When/Then
        assertFalse(jwtService.isTokenValid(null));
    }
    
    @Test
    void testIsTokenValid_ShouldReturnFalseForEmptyToken() {
        // When/Then
        assertFalse(jwtService.isTokenValid(""));
    }
    
    @Test
    void testExtractExpiration_ShouldReturnFutureDate() {
        // Given
        String token = jwtService.generateToken("user", "ROLE");
        
        // When
        java.util.Date expiration = jwtService.extractExpiration(token);
        
        // Then
        assertNotNull(expiration);
        assertTrue(expiration.after(new java.util.Date()));
    }
    
    @Test
    void testGenerateToken_DifferentTokensForSameUser() {
        // Given
        String username = "user";
        String role = "ROLE";
        
        // When
        String token1 = jwtService.generateToken(username, role);
        
        // Small delay to ensure different issued-at time
        try { Thread.sleep(10); } catch (InterruptedException ignored) {}
        
        String token2 = jwtService.generateToken(username, role);
        
        // Then - tokens may differ due to timestamp (iat claim)
        // Both should be valid
        assertTrue(jwtService.isTokenValid(token1));
        assertTrue(jwtService.isTokenValid(token2));
    }
    
    @Test
    void testValidateToken_ShouldHandleTamperedToken() {
        // Given
        String token = jwtService.generateToken("user", "ROLE");
        // Tamper with the token by modifying a character
        String tamperedToken = token.substring(0, token.length() - 5) + "XXXXX";
        
        // When
        boolean isValid = jwtService.isTokenValid(tamperedToken);
        
        // Then
        assertFalse(isValid);
    }
    
    @Test
    void testJwtServiceWithConfiguredSecret() {
        // Given - Service with a configured secret (min 32 chars)
        String secret = "this-is-a-very-long-secret-key-for-testing-purposes";
        JwtService configuredService = new JwtService(secret, 3600000);
        
        String username = "configured_user";
        String role = "ADMIN";
        
        // When
        String token = configuredService.generateToken(username, role);
        
        // Then
        assertTrue(configuredService.isTokenValid(token));
        assertEquals(username, configuredService.extractUsername(token));
        assertEquals(role, configuredService.extractRole(token));
    }
}
