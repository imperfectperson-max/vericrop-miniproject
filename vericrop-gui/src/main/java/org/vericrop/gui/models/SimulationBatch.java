package org.vericrop.gui.models;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * SimulationBatch model representing a batch record created during simulation.
 * Batches are persisted to enable report generation from the database.
 */
public class SimulationBatch {
    private UUID id;
    private UUID simulationId;
    private int batchIndex;
    private int quantity;
    private String status;  // created, in_transit, delivered, failed
    private Double qualityScore;
    private Double temperature;
    private Double humidity;
    private String currentLocation;
    private Double progress;
    private JsonNode metadata;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Status constants
    public static final String STATUS_CREATED = "created";
    public static final String STATUS_IN_TRANSIT = "in_transit";
    public static final String STATUS_DELIVERED = "delivered";
    public static final String STATUS_FAILED = "failed";
    
    // Default constructor
    public SimulationBatch() {
        this.status = STATUS_CREATED;
        this.progress = 0.0;
    }
    
    // Constructor with required fields
    public SimulationBatch(UUID simulationId, int batchIndex, int quantity) {
        this();
        this.simulationId = simulationId;
        this.batchIndex = batchIndex;
        this.quantity = quantity;
    }
    
    // Getters and setters
    
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public UUID getSimulationId() {
        return simulationId;
    }
    
    public void setSimulationId(UUID simulationId) {
        this.simulationId = simulationId;
    }
    
    public int getBatchIndex() {
        return batchIndex;
    }
    
    public void setBatchIndex(int batchIndex) {
        this.batchIndex = batchIndex;
    }
    
    public int getQuantity() {
        return quantity;
    }
    
    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public Double getQualityScore() {
        return qualityScore;
    }
    
    public void setQualityScore(Double qualityScore) {
        this.qualityScore = qualityScore;
    }
    
    public Double getTemperature() {
        return temperature;
    }
    
    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }
    
    public Double getHumidity() {
        return humidity;
    }
    
    public void setHumidity(Double humidity) {
        this.humidity = humidity;
    }
    
    public String getCurrentLocation() {
        return currentLocation;
    }
    
    public void setCurrentLocation(String currentLocation) {
        this.currentLocation = currentLocation;
    }
    
    public Double getProgress() {
        return progress;
    }
    
    public void setProgress(Double progress) {
        this.progress = progress;
    }
    
    public JsonNode getMetadata() {
        return metadata;
    }
    
    public void setMetadata(JsonNode metadata) {
        this.metadata = metadata;
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
     * Check if the batch is in transit
     */
    public boolean isInTransit() {
        return STATUS_IN_TRANSIT.equals(status);
    }
    
    /**
     * Check if the batch is delivered
     */
    public boolean isDelivered() {
        return STATUS_DELIVERED.equals(status);
    }
    
    /**
     * Check if the batch has failed
     */
    public boolean isFailed() {
        return STATUS_FAILED.equals(status);
    }
    
    @Override
    public String toString() {
        return "SimulationBatch{" +
                "id=" + id +
                ", simulationId=" + simulationId +
                ", batchIndex=" + batchIndex +
                ", quantity=" + quantity +
                ", status='" + status + '\'' +
                ", qualityScore=" + qualityScore +
                ", temperature=" + temperature +
                ", progress=" + progress +
                ", currentLocation='" + currentLocation + '\'' +
                '}';
    }
}
