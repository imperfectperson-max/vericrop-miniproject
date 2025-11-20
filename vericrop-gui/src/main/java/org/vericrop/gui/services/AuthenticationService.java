package org.vericrop.gui.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Authentication Service for handling user login and session management.
 * Simplified implementation for the VeriCrop GUI application.
 * 
 * In a production system, this would integrate with a proper authentication
 * backend (OAuth2, JWT, etc.). For now, it manages simple session state.
 */
public class AuthenticationService {
    private static final Logger logger = LoggerFactory.getLogger(AuthenticationService.class);
    
    private String currentUser;
    private String currentRole;
    private Map<String, Object> sessionData;
    private boolean authenticated;

    public AuthenticationService() {
        this.sessionData = new HashMap<>();
        this.authenticated = false;
        logger.info("✅ AuthenticationService initialized");
    }

    /**
     * Authenticate a user with given credentials
     * @param username Username
     * @param role User role (farmer, supplier, consumer, admin)
     * @return true if authentication successful
     */
    public boolean login(String username, String role) {
        if (username == null || username.trim().isEmpty()) {
            logger.warn("Login failed: empty username");
            return false;
        }
        
        // In a real system, validate credentials here
        this.currentUser = username;
        this.currentRole = role != null ? role : "user";
        this.authenticated = true;
        this.sessionData.clear();
        
        logger.info("✅ User logged in: {} (role: {})", username, this.currentRole);
        return true;
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
