package org.vericrop.gui.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.gui.models.User;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages user session state including authentication and user data.
 */
public class SessionManager {
    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);
    private static SessionManager instance;
    
    private User currentUser;
    private String sessionToken;
    private final Map<String, Object> sessionData;
    
    private SessionManager() {
        this.sessionData = new HashMap<>();
        logger.info("SessionManager initialized");
    }
    
    /**
     * Get the singleton instance.
     */
    public static synchronized SessionManager getInstance() {
        if (instance == null) {
            instance = new SessionManager();
        }
        return instance;
    }
    
    /**
     * Set the current logged-in user.
     */
    public void setCurrentUser(User user) {
        this.currentUser = user;
        if (user != null) {
            logger.info("User logged in: {} (role: {})", user.getUsername(), user.getRole());
        }
    }
    
    /**
     * Get the current logged-in user.
     */
    public User getCurrentUser() {
        return currentUser;
    }
    
    /**
     * Check if a user is currently logged in.
     */
    public boolean isLoggedIn() {
        return currentUser != null;
    }
    
    /**
     * Set the session token.
     */
    public void setSessionToken(String token) {
        this.sessionToken = token;
    }
    
    /**
     * Get the session token.
     */
    public String getSessionToken() {
        return sessionToken;
    }
    
    /**
     * Store arbitrary session data.
     */
    public void putSessionData(String key, Object value) {
        sessionData.put(key, value);
    }
    
    /**
     * Retrieve session data.
     */
    public Object getSessionData(String key) {
        return sessionData.get(key);
    }
    
    /**
     * Clear the current session (logout).
     */
    public void clearSession() {
        logger.info("Clearing session for user: {}", 
                   currentUser != null ? currentUser.getUsername() : "none");
        this.currentUser = null;
        this.sessionToken = null;
        this.sessionData.clear();
    }
    
    /**
     * Get the current user's role.
     */
    public String getCurrentRole() {
        return currentUser != null ? currentUser.getRole() : null;
    }
    
    /**
     * Get the current user's ID.
     */
    public Long getCurrentUserId() {
        return currentUser != null ? currentUser.getId() : null;
    }
    
    /**
     * Get the current user's username.
     */
    public String getCurrentUsername() {
        return currentUser != null ? currentUser.getUsername() : null;
    }
}
