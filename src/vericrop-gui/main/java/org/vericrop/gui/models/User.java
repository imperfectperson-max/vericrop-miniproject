package org.vericrop.gui.models;

import java.time.LocalDateTime;

/**
 * User model representing a user in the VeriCrop system.
 * Supports authentication, authorization, and user profile information.
 */
public class User {
    private Long id;
    private String username;
    private String email;
    private String fullName;
    private String role;  // FARMER, CONSUMER, ADMIN, SUPPLIER
    private String status; // active, inactive, suspended
    private LocalDateTime lastLogin;
    private int failedLoginAttempts;
    private LocalDateTime lockedUntil;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Default constructor
    public User() {
        this.role = "USER";
        this.status = "active";
        this.failedLoginAttempts = 0;
    }
    
    // Constructor with required fields
    public User(String username, String email, String fullName, String role) {
        this();
        this.username = username;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
    }
    
    // Getters and setters
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getFullName() {
        return fullName;
    }
    
    public void setFullName(String fullName) {
        this.fullName = fullName;
    }
    
    public String getRole() {
        return role;
    }
    
    public void setRole(String role) {
        this.role = role;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public LocalDateTime getLastLogin() {
        return lastLogin;
    }
    
    public void setLastLogin(LocalDateTime lastLogin) {
        this.lastLogin = lastLogin;
    }
    
    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }
    
    public void setFailedLoginAttempts(int failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
    }
    
    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }
    
    public void setLockedUntil(LocalDateTime lockedUntil) {
        this.lockedUntil = lockedUntil;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    /**
     * Check if the account is currently locked
     */
    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
    }
    
    /**
     * Check if the account is active
     */
    public boolean isActive() {
        return "active".equalsIgnoreCase(status);
    }
    
    /**
     * Check if user has a specific role
     */
    public boolean hasRole(String checkRole) {
        return checkRole != null && checkRole.equalsIgnoreCase(this.role);
    }
    
    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", fullName='" + fullName + '\'' +
                ", role='" + role + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
