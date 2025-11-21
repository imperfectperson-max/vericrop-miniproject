package org.vericrop.gui.services;

/**
 * Interface for authentication service implementations.
 * Allows pluggable authentication backends (REST API, in-memory, database, etc.)
 */
public interface AuthService {
    
    /**
     * Authenticate a user with username/email and password
     * @param username Username or email
     * @param password Password (plaintext)
     * @return true if authentication successful, false otherwise
     */
    boolean login(String username, String password);
    
    /**
     * Register a new user
     * @param username Username
     * @param password Password (plaintext)
     * @param email Email address
     * @param fullName Full name of user
     * @param role User role (farmer, consumer, admin, supplier)
     * @return true if registration successful, false otherwise
     */
    boolean register(String username, String password, String email, String fullName, String role);
    
    /**
     * Logout current user and clear session
     */
    void logout();
    
    /**
     * Check if a user is authenticated
     * @return true if user is authenticated
     */
    boolean isAuthenticated();
    
    /**
     * Get current authenticated username
     * @return username or null if not authenticated
     */
    String getCurrentUser();
    
    /**
     * Get current user's role
     * @return role (farmer, consumer, admin, supplier) or null if not authenticated
     */
    String getCurrentRole();
    
    /**
     * Get current user's email
     * @return email or null if not authenticated
     */
    String getCurrentEmail();
    
    /**
     * Get current user's full name
     * @return full name or null if not authenticated
     */
    String getCurrentFullName();
}
