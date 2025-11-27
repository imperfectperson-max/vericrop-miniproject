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
 * Supports both database-backed authentication with BCrypt and demo mode.
 * 
 * Database mode: Validates against PostgreSQL users table with BCrypt hashing
 * Demo mode: ONLY enabled when vericrop.demoMode system property is "true" - accepts demo credentials
 * 
 * SECURITY NOTE: Demo mode must be explicitly enabled and should never be used in production.
 */
public class AuthenticationService {
    private static final Logger logger = LoggerFactory.getLogger(AuthenticationService.class);
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_DURATION_MINUTES = 30;
    
    /** System property to enable demo mode. Must be explicitly set to "true". */
    private static final String DEMO_MODE_PROPERTY = "vericrop.demoMode";
    
    private String currentUser;
    private String currentRole;
    private String currentEmail;
    private String currentFullName;
    private Map<String, Object> sessionData;
    private boolean authenticated;
    private DataSource dataSource;
    private BCryptPasswordEncoder passwordEncoder;
    private boolean useDatabaseAuth;
    private boolean demoModeEnabled;

    /**
     * Constructor for database-backed authentication (production)
     */
    public AuthenticationService(DataSource dataSource) {
        this.dataSource = dataSource;
        this.passwordEncoder = new BCryptPasswordEncoder();
        this.sessionData = new HashMap<>();
        this.authenticated = false;
        this.useDatabaseAuth = true;
        this.demoModeEnabled = isDemoModeExplicitlyEnabled();
        
        if (demoModeEnabled) {
            logger.warn("⚠️  DEMO MODE ENABLED - Authentication is relaxed. DO NOT use in production!");
        }
        logger.info("✅ AuthenticationService initialized with database authentication (demoMode={})", demoModeEnabled);
    }

    /**
     * Constructor for demo mode (development/testing only)
     * Demo mode MUST be explicitly enabled via system property.
     */
    public AuthenticationService() {
        this.sessionData = new HashMap<>();
        this.authenticated = false;
        this.useDatabaseAuth = false;
        this.demoModeEnabled = isDemoModeExplicitlyEnabled();
        
        if (!demoModeEnabled) {
            logger.error("❌ AuthenticationService initialized without database and demo mode is not enabled. " +
                        "Authentication will fail. Set -Dvericrop.demoMode=true to enable demo mode.");
        } else {
            logger.warn("⚠️  DEMO MODE ENABLED - Authentication is relaxed. DO NOT use in production!");
        }
        logger.info("✅ AuthenticationService initialized (demoMode={}, databaseAuth={})", demoModeEnabled, useDatabaseAuth);
    }
    
    /**
     * Check if demo mode is explicitly enabled via system property.
     * Demo mode must be explicitly requested by setting vericrop.demoMode=true
     */
    private static boolean isDemoModeExplicitlyEnabled() {
        String demoMode = System.getProperty(DEMO_MODE_PROPERTY, "false");
        return "true".equalsIgnoreCase(demoMode.trim());
    }
    
    /**
     * Check if demo mode is enabled
     */
    public boolean isDemoMode() {
        return demoModeEnabled;
    }
    
    /**
     * Enable or disable demo mode programmatically.
     * This should only be called from the UI toggle.
     */
    public void setDemoMode(boolean enabled) {
        this.demoModeEnabled = enabled;
        System.setProperty(DEMO_MODE_PROPERTY, String.valueOf(enabled));
        if (enabled) {
            logger.warn("⚠️  DEMO MODE ENABLED via UI toggle");
        } else {
            logger.info("Demo mode disabled via UI toggle");
        }
    }

    /**
     * Authenticate a user with username and password.
     * 
     * In database mode: Validates credentials against the database with BCrypt.
     * In demo mode (explicitly enabled): Accepts demo credentials for testing.
     * 
     * @param username Username
     * @param password Password (plaintext)
     * @return true if authentication successful
     */
    public boolean login(String username, String password) {
        if (username == null || username.trim().isEmpty()) {
            logger.warn("Login failed: empty username");
            return false;
        }
        
        if (password == null || password.trim().isEmpty()) {
            logger.warn("Login failed: empty password");
            return false;
        }
        
        // If database auth is available, use it
        if (useDatabaseAuth && dataSource != null) {
            return authenticateWithDatabase(username, password);
        }
        
        // Only allow demo mode if explicitly enabled
        if (demoModeEnabled) {
            logger.warn("Using demo authentication for user: {}", username);
            return authenticateDemoMode(username, password);
        }
        
        // No database and demo mode not enabled - authentication fails
        logger.error("Authentication failed: Database not available and demo mode not enabled. " +
                    "Set -Dvericrop.demoMode=true to enable demo mode or configure database connection.");
        return false;
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
            
            // Only fall back to demo mode if explicitly enabled
            if (demoModeEnabled) {
                logger.warn("Database error - falling back to demo authentication mode");
                return authenticateDemoMode(username, password);
            }
            
            // Don't fallback to simple mode - this was the security issue
            logger.error("Authentication failed due to database error. Demo mode is not enabled.");
            return false;
        }
    }

    /**
     * Demo mode authentication (ONLY for testing/development).
     * This accepts predefined demo credentials when demo mode is explicitly enabled.
     * 
     * SECURITY: This should NEVER be used in production. Demo mode must be explicitly enabled.
     */
    private boolean authenticateDemoMode(String username, String password) {
        // In demo mode, we still require non-empty credentials
        if (password == null || password.trim().isEmpty()) {
            logger.warn("Demo login failed: empty password");
            return false;
        }
        
        // Validate against predefined demo accounts for security
        // Only allow specific demo usernames with their expected passwords
        String normalizedUsername = username.trim().toLowerCase();
        String role;
        
        switch (normalizedUsername) {
            case "admin":
                if (!"admin123".equals(password)) {
                    logger.warn("Demo login failed: incorrect password for demo admin account");
                    return false;
                }
                role = "ADMIN";
                break;
            case "farmer":
                if (!"farmer123".equals(password)) {
                    logger.warn("Demo login failed: incorrect password for demo farmer account");
                    return false;
                }
                role = "FARMER";
                break;
            case "supplier":
                if (!"supplier123".equals(password)) {
                    logger.warn("Demo login failed: incorrect password for demo supplier account");
                    return false;
                }
                role = "SUPPLIER";
                break;
            case "consumer":
                if (!"consumer123".equals(password)) {
                    logger.warn("Demo login failed: incorrect password for demo consumer account");
                    return false;
                }
                role = "CONSUMER";
                break;
            default:
                // Don't allow arbitrary users in demo mode
                logger.warn("Demo login failed: unknown demo account '{}'. " +
                           "Valid demo accounts: admin, farmer, supplier, consumer", username);
                return false;
        }
        
        this.currentUser = username;
        this.currentRole = role;
        this.currentEmail = username + "@vericrop.demo";
        this.currentFullName = username.substring(0, 1).toUpperCase() + username.substring(1) + " (Demo)";
        this.authenticated = true;
        this.sessionData.clear();
        
        logger.info("✅ User logged in (DEMO MODE): {} (role: {})", username, role);
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
