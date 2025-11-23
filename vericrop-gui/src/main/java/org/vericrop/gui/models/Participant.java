package org.vericrop.gui.models;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Participant model representing a connected GUI instance in the VeriCrop system.
 * Used for contact discovery and messaging between different GUI instances.
 */
public class Participant {
    /** Threshold in minutes for considering a participant online */
    public static final int ONLINE_THRESHOLD_MINUTES = 5;
    private Long id;
    private Long userId;
    private String instanceId;
    private String displayName;
    private Map<String, Object> connectionInfo;
    private String guiVersion;
    private String status;  // active, inactive, disconnected
    private LocalDateTime lastSeen;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Additional fields for display
    private String username;  // From users table
    private String userRole;  // From users table
    
    // Default constructor
    public Participant() {
        this.status = "active";
        this.connectionInfo = new HashMap<>();
    }
    
    // Constructor with required fields
    public Participant(Long userId, String instanceId, String displayName) {
        this();
        this.userId = userId;
        this.instanceId = instanceId;
        this.displayName = displayName;
        this.lastSeen = LocalDateTime.now();
    }
    
    // Getters and setters
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Long getUserId() {
        return userId;
    }
    
    public void setUserId(Long userId) {
        this.userId = userId;
    }
    
    public String getInstanceId() {
        return instanceId;
    }
    
    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
    
    public Map<String, Object> getConnectionInfo() {
        return connectionInfo;
    }
    
    public void setConnectionInfo(Map<String, Object> connectionInfo) {
        this.connectionInfo = connectionInfo;
    }
    
    public String getGuiVersion() {
        return guiVersion;
    }
    
    public void setGuiVersion(String guiVersion) {
        this.guiVersion = guiVersion;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public LocalDateTime getLastSeen() {
        return lastSeen;
    }
    
    public void setLastSeen(LocalDateTime lastSeen) {
        this.lastSeen = lastSeen;
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
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getUserRole() {
        return userRole;
    }
    
    public void setUserRole(String userRole) {
        this.userRole = userRole;
    }
    
    /**
     * Check if the participant is currently active
     */
    public boolean isActive() {
        return "active".equalsIgnoreCase(status);
    }
    
    /**
     * Check if the participant has been seen recently (within threshold)
     */
    public boolean isOnline() {
        if (lastSeen == null) {
            return false;
        }
        return lastSeen.isAfter(LocalDateTime.now().minusMinutes(ONLINE_THRESHOLD_MINUTES)) && isActive();
    }
    
    /**
     * Update last seen timestamp to current time
     */
    public void updateLastSeen() {
        this.lastSeen = LocalDateTime.now();
    }
    
    /**
     * Get connection endpoint from connectionInfo
     */
    public String getConnectionEndpoint() {
        if (connectionInfo != null && connectionInfo.containsKey("endpoint")) {
            return (String) connectionInfo.get("endpoint");
        }
        return null;
    }
    
    /**
     * Set connection endpoint in connectionInfo
     */
    public void setConnectionEndpoint(String endpoint) {
        if (connectionInfo == null) {
            connectionInfo = new HashMap<>();
        }
        connectionInfo.put("endpoint", endpoint);
    }
    
    @Override
    public String toString() {
        return "Participant{" +
                "id=" + id +
                ", userId=" + userId +
                ", instanceId='" + instanceId + '\'' +
                ", displayName='" + displayName + '\'' +
                ", status='" + status + '\'' +
                ", lastSeen=" + lastSeen +
                ", isOnline=" + isOnline() +
                '}';
    }
}
