package org.vericrop.gui.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Authentication Service for handling user login and session management.
 * Supports both database-backed authentication with BCrypt and simple mode.
 * 
 * Database mode: Validates against PostgreSQL users table with BCrypt hashing
 * Simple mode: Used when database is unavailable (fallback for development)
 */
public class AuthenticationService {
    private static final Logger logger = LoggerFactory.getLogger(AuthenticationService.class);
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_DURATION_MINUTES = 30;
    
    private String currentUser;
    private String currentRole;
    private String currentEmail;
    private String currentFullName;
    private Map<String, Object> sessionData;
    private boolean authenticated;
    private DataSource dataSource;
    private BCryptPasswordEncoder passwordEncoder;
    private boolean useDatabaseAuth;

    /**
     * Constructor for database-backed authentication (production)
     */
    public AuthenticationService(DataSource dataSource) {
        this.dataSource = dataSource;
        this.passwordEncoder = new BCryptPasswordEncoder();
        this.sessionData = new HashMap<>();
        this.authenticated = false;
        this.useDatabaseAuth = true;
        logger.info("✅ AuthenticationService initialized with database authentication");
    }

    /**
     * Constructor for simple mode (development/testing)
     */
    public AuthenticationService() {
        this.sessionData = new HashMap<>();
        this.authenticated = false;
        this.useDatabaseAuth = false;
        logger.info("✅ AuthenticationService initialized in simple mode (no database)");
    }

    /**
     * Authenticate a user with username and password
     * @param username Username
     * @param password Password (plaintext)
     * @return true if authentication successful
     */
    public boolean login(String username, String password) {
        if (username == null || username.trim().isEmpty()) {
            logger.warn("Login failed: empty username");
            return false;
        }
        
        if (useDatabaseAuth && dataSource != null) {
            return authenticateWithDatabase(username, password);
        } else {
            // Fallback to simple mode (for demo or when database unavailable)
            return authenticateSimple(username, password);
        }
    }

    /**
     * Authenticate with database using BCrypt
     */
    private boolean authenticateWithDatabase(String username, String password) {
        String sql = "SELECT id, username, password_hash, email, full_name, role, status, " +
                     "failed_login_attempts, locked_until FROM users WHERE username = ? AND status = 'active'";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String passwordHash = rs.getString("password_hash");
                    int failedAttempts = rs.getInt("failed_login_attempts");
                    Timestamp lockedUntil = rs.getTimestamp("locked_until");
                    
                    // Check if account is locked
                    if (lockedUntil != null && lockedUntil.toLocalDateTime().isAfter(LocalDateTime.now())) {
                        logger.warn("Login failed: account locked until {} for user: {}", lockedUntil, username);
                        return false;
                    }
                    
                    // Verify password
                    if (passwordEncoder.matches(password, passwordHash)) {
                        // Success - reset failed attempts and update last login
                        resetFailedAttempts(username);
                        updateLastLogin(username);
                        
                        // Set session data
                        this.currentUser = rs.getString("username");
                        this.currentRole = rs.getString("role");
                        this.currentEmail = rs.getString("email");
                        this.currentFullName = rs.getString("full_name");
                        this.authenticated = true;
                        this.sessionData.clear();
                        
                        logger.info("✅ User authenticated via database: {} (role: {})", currentUser, currentRole);
                        return true;
                    } else {
                        // Failed login - increment counter
                        incrementFailedAttempts(username, failedAttempts);
                        logger.warn("Login failed: incorrect password for user: {}", username);
                        return false;
                    }
                } else {
                    logger.warn("Login failed: user not found: {}", username);
                    return false;
                }
            }
        } catch (SQLException e) {
            logger.error("Database authentication error: {}", e.getMessage());
            // Fallback to simple mode on database error
            logger.warn("Falling back to simple authentication mode");
            return authenticateSimple(username, password);
        }
    }

    /**
     * Simple authentication (development/demo mode)
     */
    private boolean authenticateSimple(String username, String password) {
        // Simple validation for demo purposes
        if (password == null || password.trim().isEmpty()) {
            logger.warn("Login failed: empty password");
            return false;
        }
        
        // Determine role from username for demo
        String role = "USER";
        if (username.equalsIgnoreCase("admin")) {
            role = "ADMIN";
        } else if (username.equalsIgnoreCase("farmer")) {
            role = "FARMER";
        } else if (username.equalsIgnoreCase("supplier")) {
            role = "SUPPLIER";
        } else if (username.equalsIgnoreCase("consumer")) {
            role = "CONSUMER";
        }
        
        this.currentUser = username;
        this.currentRole = role;
        this.currentEmail = username + "@vericrop.local";
        this.currentFullName = username;
        this.authenticated = true;
        this.sessionData.clear();
        
        logger.info("✅ User logged in (simple mode): {} (role: {})", username, role);
        return true;
    }

    private void resetFailedAttempts(String username) {
        String sql = "UPDATE users SET failed_login_attempts = 0, locked_until = NULL WHERE username = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error resetting failed attempts: {}", e.getMessage());
        }
    }

    private void updateLastLogin(String username) {
        String sql = "UPDATE users SET last_login = CURRENT_TIMESTAMP WHERE username = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error updating last login: {}", e.getMessage());
        }
    }

    private void incrementFailedAttempts(String username, int currentAttempts) {
        int newAttempts = currentAttempts + 1;
        String sql;
        
        if (newAttempts >= MAX_FAILED_ATTEMPTS) {
            // Lock account
            sql = "UPDATE users SET failed_login_attempts = ?, locked_until = CURRENT_TIMESTAMP + INTERVAL '" + 
                  LOCKOUT_DURATION_MINUTES + " minutes' WHERE username = ?";
            logger.warn("Account locked due to too many failed attempts: {}", username);
        } else {
            sql = "UPDATE users SET failed_login_attempts = ? WHERE username = ?";
        }
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, newAttempts);
            stmt.setString(2, username);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error incrementing failed attempts: {}", e.getMessage());
        }
    }

    /**
     * Logout current user and clear session
     */
    public void logout() {
        if (authenticated) {
            logger.info("User logged out: {}", currentUser);
        }
        
        this.currentUser = null;
        this.currentRole = null;
        this.authenticated = false;
        this.sessionData.clear();
    }

    /**
     * Check if user is authenticated
     */
    public boolean isAuthenticated() {
        return authenticated;
    }

    /**
     * Get current authenticated user
     */
    public String getCurrentUser() {
        return currentUser;
    }

    /**
     * Get current user role
     */
    public String getCurrentRole() {
        return currentRole;
    }

    /**
     * Get current user email
     */
    public String getCurrentEmail() {
        return currentEmail;
    }

    /**
     * Get current user full name
     */
    public String getCurrentFullName() {
        return currentFullName;
    }

    /**
     * Store data in session
     */
    public void setSessionData(String key, Object value) {
        sessionData.put(key, value);
    }

    /**
     * Retrieve data from session
     */
    public Object getSessionData(String key) {
        return sessionData.get(key);
    }

    /**
     * Check if user has specific role
     */
    public boolean hasRole(String role) {
        return role != null && role.equalsIgnoreCase(currentRole);
    }

    /**
     * Check if user is farmer
     */
    public boolean isFarmer() {
        return hasRole("farmer");
    }

    /**
     * Check if user is supplier/logistics
     */
    public boolean isSupplier() {
        return hasRole("supplier") || hasRole("logistics");
    }

    /**
     * Check if user is consumer
     */
    public boolean isConsumer() {
        return hasRole("consumer");
    }

    /**
     * Check if user is admin
     */
    public boolean isAdmin() {
        return hasRole("admin");
    }
}
