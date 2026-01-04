package org.vericrop.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Event published for batch lifecycle updates
 */
public class BatchUpdateEvent {
    private String batchId;
    private String status; // CREATED, DISPATCHED, IN_TRANSIT, DELIVERED
    private long timestamp;
    private String location;
    private Double qualityScore;
    private String details;
    private Double primeRate;
    private Double rejectionRate;
    
    public BatchUpdateEvent() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public BatchUpdateEvent(String batchId, String status) {
        this();
        this.batchId = batchId;
        this.status = status;
    }
    
    public BatchUpdateEvent(String batchId, String status, String location, Double qualityScore, String details) {
        this();
        this.batchId = batchId;
        this.status = status;
        this.location = location;
        this.qualityScore = qualityScore;
        this.details = details;
    }
    
    // Getters and setters
    @JsonProperty("batch_id")
    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    
    @JsonProperty("quality_score")
    public Double getQualityScore() { return qualityScore; }
    public void setQualityScore(Double qualityScore) { this.qualityScore = qualityScore; }
    
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    
    @JsonProperty("prime_rate")
    public Double getPrimeRate() { return primeRate; }
    public void setPrimeRate(Double primeRate) { this.primeRate = primeRate; }
    
    @JsonProperty("rejection_rate")
    public Double getRejectionRate() { return rejectionRate; }
    public void setRejectionRate(Double rejectionRate) { this.rejectionRate = rejectionRate; }
    
    @Override
    public String toString() {
        return String.format("BatchUpdateEvent{batchId='%s', status='%s', location='%s'}",
                batchId, status, location);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BatchUpdateEvent that = (BatchUpdateEvent) o;
        return timestamp == that.timestamp &&
                Objects.equals(batchId, that.batchId) &&
                Objects.equals(status, that.status);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(batchId, status, timestamp);
    }
}
