package org.vericrop.gui.persistence;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Represents a persisted shipment record for historical data and reports.
 */
public class PersistedShipment {
    
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("batch_id")
    private String batchId;
    
    @JsonProperty("farmer_id")
    private String farmerId;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("location")
    private String location;
    
    @JsonProperty("temperature")
    private double temperature;
    
    @JsonProperty("humidity")
    private double humidity;
    
    @JsonProperty("eta")
    private String eta;
    
    @JsonProperty("vehicle")
    private String vehicle;
    
    @JsonProperty("created_at")
    private long createdAt;
    
    @JsonProperty("updated_at")
    private long updatedAt;
    
    @JsonProperty("origin")
    private String origin;
    
    @JsonProperty("destination")
    private String destination;
    
    @JsonProperty("quality_score")
    private double qualityScore;
    
    /**
     * Default constructor for JSON deserialization
     */
    public PersistedShipment() {
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
    }
    
    /**
     * Constructor with basic fields
     */
    public PersistedShipment(String batchId, String status, String location, 
                             double temperature, double humidity, String eta, String vehicle) {
        this();
        this.id = java.util.UUID.randomUUID().toString();
        this.batchId = batchId;
        this.status = status;
        this.location = location;
        this.temperature = temperature;
        this.humidity = humidity;
        this.eta = eta;
        this.vehicle = vehicle;
    }
    
    /**
     * Full constructor
     */
    public PersistedShipment(String id, String batchId, String farmerId, String status,
                             String location, double temperature, double humidity,
                             String eta, String vehicle, String origin, String destination,
                             double qualityScore) {
        this.id = id;
        this.batchId = batchId;
        this.farmerId = farmerId;
        this.status = status;
        this.location = location;
        this.temperature = temperature;
        this.humidity = humidity;
        this.eta = eta;
        this.vehicle = vehicle;
        this.origin = origin;
        this.destination = destination;
        this.qualityScore = qualityScore;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
    }

    // Getters and setters
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getBatchId() {
        return batchId;
    }
    
    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }
    
    public String getFarmerId() {
        return farmerId;
    }
    
    public void setFarmerId(String farmerId) {
        this.farmerId = farmerId;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
        this.updatedAt = System.currentTimeMillis();
    }
    
    public String getLocation() {
        return location;
    }
    
    public void setLocation(String location) {
        this.location = location;
        this.updatedAt = System.currentTimeMillis();
    }
    
    public double getTemperature() {
        return temperature;
    }
    
    public void setTemperature(double temperature) {
        this.temperature = temperature;
        this.updatedAt = System.currentTimeMillis();
    }
    
    public double getHumidity() {
        return humidity;
    }
    
    public void setHumidity(double humidity) {
        this.humidity = humidity;
        this.updatedAt = System.currentTimeMillis();
    }
    
    public String getEta() {
        return eta;
    }
    
    public void setEta(String eta) {
        this.eta = eta;
        this.updatedAt = System.currentTimeMillis();
    }
    
    public String getVehicle() {
        return vehicle;
    }
    
    public void setVehicle(String vehicle) {
        this.vehicle = vehicle;
    }
    
    public long getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
    
    public long getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public String getOrigin() {
        return origin;
    }
    
    public void setOrigin(String origin) {
        this.origin = origin;
    }
    
    public String getDestination() {
        return destination;
    }
    
    public void setDestination(String destination) {
        this.destination = destination;
    }
    
    public double getQualityScore() {
        return qualityScore;
    }
    
    public void setQualityScore(double qualityScore) {
        this.qualityScore = qualityScore;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PersistedShipment that = (PersistedShipment) o;
        return Objects.equals(id, that.id) && Objects.equals(batchId, that.batchId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id, batchId);
    }
    
    @Override
    public String toString() {
        return "PersistedShipment{" +
                "id='" + id + '\'' +
                ", batchId='" + batchId + '\'' +
                ", status='" + status + '\'' +
                ", location='" + location + '\'' +
                ", temperature=" + temperature +
                ", createdAt=" + createdAt +
                '}';
    }
}
