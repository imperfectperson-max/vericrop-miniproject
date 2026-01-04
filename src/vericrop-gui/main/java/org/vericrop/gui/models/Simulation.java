package org.vericrop.gui.models;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Simulation model representing a simulation session in the VeriCrop system.
 * Supports multi-user access control through owner, supplier, and consumer relationships.
 * Enables multi-device/multi-instance access via simulation_token.
 */
public class Simulation {
    private UUID id;
    private String title;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private String status;  // created, running, completed, failed, stopped
    private Long ownerUserId;
    private Long supplierUserId;
    private Long consumerUserId;
    private String simulationToken;
    private JsonNode meta;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Additional fields for convenience (not stored in DB directly)
    private String ownerUsername;
    private String supplierUsername;
    private String consumerUsername;
    
    // Status constants
    public static final String STATUS_CREATED = "created";
    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_STOPPED = "stopped";
    
    // Default constructor
    public Simulation() {
        this.status = STATUS_CREATED;
        this.startedAt = LocalDateTime.now();
    }
    
    // Constructor with required fields
    public Simulation(String title, Long ownerUserId, Long supplierUserId, Long consumerUserId, String simulationToken) {
        this();
        this.title = title;
        this.ownerUserId = ownerUserId;
        this.supplierUserId = supplierUserId;
        this.consumerUserId = consumerUserId;
        this.simulationToken = simulationToken;
    }
    
    // Getters and setters
    
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public LocalDateTime getStartedAt() {
        return startedAt;
    }
    
    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }
    
    public LocalDateTime getEndedAt() {
        return endedAt;
    }
    
    public void setEndedAt(LocalDateTime endedAt) {
        this.endedAt = endedAt;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public Long getOwnerUserId() {
        return ownerUserId;
    }
    
    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }
    
    public Long getSupplierUserId() {
        return supplierUserId;
    }
    
    public void setSupplierUserId(Long supplierUserId) {
        this.supplierUserId = supplierUserId;
    }
    
    public Long getConsumerUserId() {
        return consumerUserId;
    }
    
    public void setConsumerUserId(Long consumerUserId) {
        this.consumerUserId = consumerUserId;
    }
    
    public String getSimulationToken() {
        return simulationToken;
    }
    
    public void setSimulationToken(String simulationToken) {
        this.simulationToken = simulationToken;
    }
    
    public JsonNode getMeta() {
        return meta;
    }
    
    public void setMeta(JsonNode meta) {
        this.meta = meta;
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
    
    public String getOwnerUsername() {
        return ownerUsername;
    }
    
    public void setOwnerUsername(String ownerUsername) {
        this.ownerUsername = ownerUsername;
    }
    
    public String getSupplierUsername() {
        return supplierUsername;
    }
    
    public void setSupplierUsername(String supplierUsername) {
        this.supplierUsername = supplierUsername;
    }
    
    public String getConsumerUsername() {
        return consumerUsername;
    }
    
    public void setConsumerUsername(String consumerUsername) {
        this.consumerUsername = consumerUsername;
    }
    
    /**
     * Check if the simulation is currently running
     */
    public boolean isRunning() {
        return STATUS_RUNNING.equals(status);
    }
    
    /**
     * Check if the simulation is completed
     */
    public boolean isCompleted() {
        return STATUS_COMPLETED.equals(status);
    }
    
    /**
     * Check if the simulation has ended (completed, failed, or stopped)
     */
    public boolean hasEnded() {
        return STATUS_COMPLETED.equals(status) || STATUS_FAILED.equals(status) || STATUS_STOPPED.equals(status);
    }
    
    /**
     * Check if a user can access this simulation.
     * Access is granted if the user is the owner, supplier, or consumer.
     * 
     * @param userId The user ID to check
     * @return true if the user can access this simulation
     */
    public boolean canUserAccess(Long userId) {
        if (userId == null) {
            return false;
        }
        return userId.equals(ownerUserId) || 
               userId.equals(supplierUserId) || 
               userId.equals(consumerUserId);
    }
    
    @Override
    public String toString() {
        return "Simulation{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", status='" + status + '\'' +
                ", ownerUserId=" + ownerUserId +
                ", supplierUserId=" + supplierUserId +
                ", consumerUserId=" + consumerUserId +
                ", simulationToken='" + (simulationToken != null ? simulationToken.substring(0, Math.min(8, simulationToken.length())) + "..." : "null") + '\'' +
                ", startedAt=" + startedAt +
                '}';
    }
}
