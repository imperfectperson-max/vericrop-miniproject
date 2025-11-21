package org.vericrop.gui.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ValidationUtil.
 */
public class ValidationUtilTest {
    
    @Test
    public void testValidEmails() {
        assertTrue(ValidationUtil.isValidEmail("user@example.com"), "Simple email should be valid");
        assertTrue(ValidationUtil.isValidEmail("user.name@example.com"), "Email with dot should be valid");
        assertTrue(ValidationUtil.isValidEmail("user+tag@example.co.uk"), "Email with plus and subdomain should be valid");
        assertTrue(ValidationUtil.isValidEmail("user_name@example-domain.com"), "Email with underscore and hyphen should be valid");
        assertTrue(ValidationUtil.isValidEmail("123@example.com"), "Numeric local part should be valid");
    }
    
    @Test
    public void testInvalidEmails() {
        assertFalse(ValidationUtil.isValidEmail(null), "Null should be invalid");
        assertFalse(ValidationUtil.isValidEmail(""), "Empty string should be invalid");
        assertFalse(ValidationUtil.isValidEmail("   "), "Whitespace should be invalid");
        assertFalse(ValidationUtil.isValidEmail("notanemail"), "Missing @ should be invalid");
        assertFalse(ValidationUtil.isValidEmail("@example.com"), "Missing local part should be invalid");
        assertFalse(ValidationUtil.isValidEmail("user@"), "Missing domain should be invalid");
        assertFalse(ValidationUtil.isValidEmail("user@.com"), "Missing domain name should be invalid");
        assertFalse(ValidationUtil.isValidEmail("user@domain"), "Missing TLD should be invalid");
        assertFalse(ValidationUtil.isValidEmail("user@@domain.com"), "Double @ should be invalid");
        assertFalse(ValidationUtil.isValidEmail("user..name@domain.com"), "Consecutive dots should be invalid");
    }
    
    @Test
    public void testValidUsername() {
        assertTrue(ValidationUtil.isValidUsername("user", 3), "Username with min length should be valid");
        assertTrue(ValidationUtil.isValidUsername("user123", 3), "Username with numbers should be valid");
        assertTrue(ValidationUtil.isValidUsername("  user  ", 3), "Username with whitespace (trimmed) should be valid");
        assertTrue(ValidationUtil.isValidUsername("a".repeat(20), 3), "Long username should be valid");
    }
    
    @Test
    public void testInvalidUsername() {
        assertFalse(ValidationUtil.isValidUsername(null, 3), "Null username should be invalid");
        assertFalse(ValidationUtil.isValidUsername("", 3), "Empty username should be invalid");
        assertFalse(ValidationUtil.isValidUsername("   ", 3), "Whitespace-only username should be invalid");
        assertFalse(ValidationUtil.isValidUsername("ab", 3), "Username shorter than min length should be invalid");
    }
    
    @Test
    public void testValidPassword() {
        assertTrue(ValidationUtil.isValidPassword("password", 6), "Password with min length should be valid");
        assertTrue(ValidationUtil.isValidPassword("123456", 6), "Numeric password should be valid");
        assertTrue(ValidationUtil.isValidPassword("pass word", 6), "Password with space should be valid");
        assertTrue(ValidationUtil.isValidPassword("P@ssw0rd!", 6), "Complex password should be valid");
    }
    
    @Test
    public void testInvalidPassword() {
        assertFalse(ValidationUtil.isValidPassword(null, 6), "Null password should be invalid");
        assertFalse(ValidationUtil.isValidPassword("", 6), "Empty password should be invalid");
        assertFalse(ValidationUtil.isValidPassword("pass", 6), "Password shorter than min length should be invalid");
        assertFalse(ValidationUtil.isValidPassword("12345", 6), "5-char password with min 6 should be invalid");
    }
    
    @Test
    public void testPasswordNoTrimming() {
        // Passwords should NOT be trimmed - spaces are intentional
        assertTrue(ValidationUtil.isValidPassword("  pass  ", 6), "Password with spaces should count spaces in length");
        assertTrue(ValidationUtil.isValidPassword("     ", 5), "5 spaces should be valid if min is 5");
    }
    
    @Test
    public void testEmailTrimming() {
        // Email validation should handle trimmed input
        assertTrue(ValidationUtil.isValidEmail("  user@example.com  "), "Email with leading/trailing spaces should be valid after trim");
    }
    
    @Test
    public void testUsernameTrimming() {
        // Username validation should handle trimmed input
        assertTrue(ValidationUtil.isValidUsername("  abc  ", 3), "Username with spaces should be valid after trim");
        assertFalse(ValidationUtil.isValidUsername("  ab  ", 3), "Username too short after trim should be invalid");
    }
}
