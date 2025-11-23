package org.vericrop.service.models;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents an alert generated during delivery simulation.
 */
public class Alert {
    private final String id;
    private final String batchId;
    private final AlertType type;
    private final Severity severity;
    private final String message;
    private final double currentValue;
    private final double thresholdValue;
    private final long timestamp;
    private final String location;
    
    public Alert(
            @JsonProperty("id") String id,
            @JsonProperty("batchId") String batchId,
            @JsonProperty("type") AlertType type,
            @JsonProperty("severity") Severity severity,
            @JsonProperty("message") String message,
            @JsonProperty("currentValue") double currentValue,
            @JsonProperty("thresholdValue") double thresholdValue,
            @JsonProperty("timestamp") long timestamp,
            @JsonProperty("location") String location) {
        this.id = id;
        this.batchId = batchId;
        this.type = type;
        this.severity = severity;
        this.message = message;
        this.currentValue = currentValue;
        this.thresholdValue = thresholdValue;
        this.timestamp = timestamp;
        this.location = location;
    }
    
    public String getId() { return id; }
    public String getBatchId() { return batchId; }
    public AlertType getType() { return type; }
    public Severity getSeverity() { return severity; }
    public String getMessage() { return message; }
    public double getCurrentValue() { return currentValue; }
    public double getThresholdValue() { return thresholdValue; }
    public long getTimestamp() { return timestamp; }
    public String getLocation() { return location; }
    
    /**
     * Alert type enumeration.
     */
    public enum AlertType {
        TEMPERATURE_HIGH,
        TEMPERATURE_LOW,
        HUMIDITY_HIGH,
        HUMIDITY_LOW,
        SPOILAGE_RISK,
        DELIVERY_DELAY,
        QUALITY_DEGRADATION
    }
    
    /**
     * Alert severity levels.
     */
    public enum Severity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
    
    @Override
    public String toString() {
        return String.format("Alert[%s, %s, %s: %s]", batchId, severity, type, message);
    }
}
