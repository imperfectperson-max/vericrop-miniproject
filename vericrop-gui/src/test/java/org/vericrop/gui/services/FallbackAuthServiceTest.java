package org.vericrop.gui.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FallbackAuthService.
 * Tests in-memory authentication logic and session management.
 */
public class FallbackAuthServiceTest {
    
    private FallbackAuthService authService;
    
    @BeforeEach
    public void setUp() {
        authService = new FallbackAuthService();
    }
    
    @Test
    public void testLoginWithValidCredentials() {
        // Test login with pre-populated test user
        boolean result = authService.login("farmer", "password");
        
        assertTrue(result, "Login should succeed with valid credentials");
        assertTrue(authService.isAuthenticated(), "User should be authenticated");
        assertEquals("farmer", authService.getCurrentUser(), "Current user should be 'farmer'");
        assertEquals("farmer", authService.getCurrentRole(), "Role should be 'farmer'");
    }
    
    @Test
    public void testLoginWithInvalidPassword() {
        boolean result = authService.login("farmer", "wrongpassword");
        
        assertFalse(result, "Login should fail with invalid password");
        assertFalse(authService.isAuthenticated(), "User should not be authenticated");
        assertNull(authService.getCurrentUser(), "Current user should be null");
    }
    
    @Test
    public void testLoginWithNonexistentUser() {
        boolean result = authService.login("nonexistent", "password");
        
        assertFalse(result, "Login should fail for nonexistent user");
        assertFalse(authService.isAuthenticated(), "User should not be authenticated");
    }
    
    @Test
    public void testLoginWithEmptyUsername() {
        boolean result = authService.login("", "password");
        
        assertFalse(result, "Login should fail with empty username");
        assertFalse(authService.isAuthenticated(), "User should not be authenticated");
    }
    
    @Test
    public void testLoginWithEmptyPassword() {
        boolean result = authService.login("farmer", "");
        
        assertFalse(result, "Login should fail with empty password");
        assertFalse(authService.isAuthenticated(), "User should not be authenticated");
    }
    
    @Test
    public void testLoginWithNullCredentials() {
        boolean result1 = authService.login(null, "password");
        assertFalse(result1, "Login should fail with null username");
        
        boolean result2 = authService.login("farmer", null);
        assertFalse(result2, "Login should fail with null password");
    }
    
    @Test
    public void testRegisterNewUser() {
        boolean result = authService.register("testuser", "password123", 
                                              "test@example.com", "Test User", "consumer");
        
        assertTrue(result, "Registration should succeed with valid data");
        
        // Verify we can login with new user
        boolean loginResult = authService.login("testuser", "password123");
        assertTrue(loginResult, "Should be able to login with newly registered user");
        assertEquals("consumer", authService.getCurrentRole(), "Role should be 'consumer'");
    }
    
    @Test
    public void testRegisterWithShortPassword() {
        boolean result = authService.register("testuser", "pass", 
                                              "test@example.com", "Test User", "consumer");
        
        assertFalse(result, "Registration should fail with password shorter than 6 characters");
    }
    
    @Test
    public void testRegisterWithInvalidEmail() {
        boolean result = authService.register("testuser", "password123", 
                                              "invalidemail", "Test User", "consumer");
        
        assertFalse(result, "Registration should fail with invalid email");
    }
    
    @Test
    public void testRegisterDuplicateUsername() {
        // Try to register a username that already exists
        boolean result = authService.register("farmer", "password123", 
                                              "new@example.com", "New Farmer", "farmer");
        
        assertFalse(result, "Registration should fail for duplicate username");
    }
    
    @Test
    public void testRegisterWithInvalidRole() {
        // Register with invalid role - should default to consumer
        boolean result = authService.register("testuser", "password123", 
                                              "test@example.com", "Test User", "invalidrole");
        
        assertTrue(result, "Registration should succeed and default to consumer role");
        
        authService.login("testuser", "password123");
        assertEquals("consumer", authService.getCurrentRole(), "Role should default to 'consumer'");
    }
    
    @Test
    public void testRegisterCaseInsensitive() {
        // Register with uppercase username
        boolean result = authService.register("TestUser", "password123", 
                                              "test@example.com", "Test User", "consumer");
        
        assertTrue(result, "Registration should succeed");
        
        // Login with lowercase should work
        boolean loginResult = authService.login("testuser", "password123");
        assertTrue(loginResult, "Login should work with lowercase username");
    }
    
    @Test
    public void testLogout() {
        // Login first
        authService.login("farmer", "password");
        assertTrue(authService.isAuthenticated(), "Should be authenticated after login");
        
        // Logout
        authService.logout();
        assertFalse(authService.isAuthenticated(), "Should not be authenticated after logout");
        assertNull(authService.getCurrentUser(), "Current user should be null after logout");
        assertNull(authService.getCurrentRole(), "Current role should be null after logout");
    }
    
    @Test
    public void testMultipleSessions() {
        // Login with first user
        authService.login("farmer", "password");
        assertEquals("farmer", authService.getCurrentUser());
        
        // Login with different user (should replace session)
        authService.login("consumer", "password");
        assertEquals("consumer", authService.getCurrentUser());
        assertEquals("consumer", authService.getCurrentRole());
    }
    
    @Test
    public void testGetCurrentEmail() {
        authService.login("farmer", "password");
        
        String email = authService.getCurrentEmail();
        assertNotNull(email, "Email should not be null");
        assertTrue(email.contains("@"), "Email should contain @");
    }
    
    @Test
    public void testGetCurrentFullName() {
        authService.login("farmer", "password");
        
        String fullName = authService.getCurrentFullName();
        assertNotNull(fullName, "Full name should not be null");
    }
    
    @Test
    public void testPrePopulatedUsers() {
        // Test all pre-populated users
        String[] users = {"farmer", "consumer", "supplier", "admin"};
        
        for (String user : users) {
            FallbackAuthService service = new FallbackAuthService();
            boolean result = service.login(user, "password");
            assertTrue(result, "Should be able to login with " + user);
            assertNotNull(service.getCurrentRole(), "Role should not be null for " + user);
            assertEquals(user.toLowerCase(), service.getCurrentRole().toLowerCase(), 
                        "Role should match username for " + user);
        }
    }
}
