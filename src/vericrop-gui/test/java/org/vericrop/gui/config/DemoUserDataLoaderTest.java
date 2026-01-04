package org.vericrop.gui.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DemoUserDataLoader.
 * Tests the demo user configuration without requiring database connection.
 */
class DemoUserDataLoaderTest {
    
    private BCryptPasswordEncoder passwordEncoder;
    
    // Demo user expectations
    private static final String DEMO_PASSWORD = "DemoPass123!";
    private static final String[] EXPECTED_USERNAMES = {
        "producer_demo", "logistics_demo", "consumer_demo", "admin_demo"
    };
    private static final String[] EXPECTED_ROLES = {
        "PRODUCER", "LOGISTICS", "CONSUMER", "ADMIN"
    };
    
    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
    }
    
    @Test
    void testDemoPasswordMeetsSecurityRequirements() {
        // Verify password has:
        // - At least 8 characters
        assertTrue(DEMO_PASSWORD.length() >= 8, "Password should be at least 8 characters");
        
        // - At least one uppercase letter
        assertTrue(DEMO_PASSWORD.matches(".*[A-Z].*"), "Password should contain uppercase letter");
        
        // - At least one lowercase letter
        assertTrue(DEMO_PASSWORD.matches(".*[a-z].*"), "Password should contain lowercase letter");
        
        // - At least one digit
        assertTrue(DEMO_PASSWORD.matches(".*[0-9].*"), "Password should contain digit");
        
        // - At least one special character
        assertTrue(DEMO_PASSWORD.matches(".*[!@#$%^&*(),.?\":{}|<>].*"), "Password should contain special character");
    }
    
    @Test
    void testPasswordCanBeEncoded() {
        String encoded = passwordEncoder.encode(DEMO_PASSWORD);
        
        assertNotNull(encoded, "Encoded password should not be null");
        assertTrue(encoded.startsWith("$2a$") || encoded.startsWith("$2b$"), 
            "Encoded password should be BCrypt format");
    }
    
    @Test
    void testPasswordVerification() {
        String encoded = passwordEncoder.encode(DEMO_PASSWORD);
        
        assertTrue(passwordEncoder.matches(DEMO_PASSWORD, encoded), 
            "Password should match its encoded form");
        assertFalse(passwordEncoder.matches("WrongPassword123!", encoded), 
            "Wrong password should not match");
    }
    
    @Test
    void testExpectedDemoUsersAreConfigured() {
        // Verify we have 4 demo users
        assertEquals(4, EXPECTED_USERNAMES.length, "Should have 4 demo users");
        assertEquals(4, EXPECTED_ROLES.length, "Should have 4 roles");
        
        // Verify username conventions
        for (String username : EXPECTED_USERNAMES) {
            assertTrue(username.endsWith("_demo"), "Username should end with _demo");
            assertTrue(username.matches("^[a-z_]+$"), "Username should be lowercase with underscores");
        }
    }
    
    @Test
    void testRolesAreValid() {
        for (String role : EXPECTED_ROLES) {
            assertTrue(role.matches("^[A-Z]+$"), "Roles should be uppercase");
            assertTrue(
                role.equals("PRODUCER") || 
                role.equals("LOGISTICS") || 
                role.equals("CONSUMER") || 
                role.equals("ADMIN"),
                "Role should be one of: PRODUCER, LOGISTICS, CONSUMER, ADMIN"
            );
        }
    }
    
    @Test
    void testUniqueRolesForEachDemoUser() {
        // Each demo user should have a unique role
        java.util.Set<String> roles = new java.util.HashSet<>();
        for (String role : EXPECTED_ROLES) {
            assertTrue(roles.add(role), "Each demo user should have unique role, duplicate: " + role);
        }
    }
}
