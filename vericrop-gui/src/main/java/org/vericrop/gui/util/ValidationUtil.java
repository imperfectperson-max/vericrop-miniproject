package org.vericrop.gui.util;

import java.util.regex.Pattern;

/**
 * Utility class for common validation operations.
 */
public class ValidationUtil {
    
    // RFC 5322 compliant email regex (simplified but robust version)
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@" +
        "(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    );
    
    /**
     * Validate email address format
     * @param email Email address to validate
     * @return true if email format is valid
     */
    public static boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email.trim()).matches();
    }
    
    /**
     * Validate username format
     * @param username Username to validate
     * @param minLength Minimum length required
     * @return true if username is valid
     */
    public static boolean isValidUsername(String username, int minLength) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        return username.trim().length() >= minLength;
    }
    
    /**
     * Validate password strength
     * @param password Password to validate
     * @param minLength Minimum length required
     * @return true if password meets requirements
     */
    public static boolean isValidPassword(String password, int minLength) {
        if (password == null || password.isEmpty()) {
            return false;
        }
        return password.length() >= minLength;
    }
    
    private ValidationUtil() {
        // Utility class - prevent instantiation
    }
}
