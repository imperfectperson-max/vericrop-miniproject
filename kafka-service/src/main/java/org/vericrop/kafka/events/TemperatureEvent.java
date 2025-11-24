package org.vericrop.kafka.events;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Event for temperature monitoring updates and violations.
 */
public class TemperatureEvent {
    private String batchId;
    private String eventType; // "READING", "VIOLATION", "COMPLIANT", "MONITORING_STARTED", "MONITORING_STOPPED"
    private double temperature;
    private double minTemp;
    private double maxTemp;
    private double avgTemp;
    private int violationCount;
    private boolean compliant;
    private long timestamp;
    
    public TemperatureEvent() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public TemperatureEvent(String batchId, String eventType, double temperature, boolean compliant) {
        this();
        this.batchId = batchId;
        this.eventType = eventType;
        this.temperature = temperature;
        this.compliant = compliant;
    }
    
    // Getters and Setters
    @JsonProperty("batch_id")
    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }
    
    @JsonProperty("event_type")
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    
    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
    
    @JsonProperty("min_temp")
    public double getMinTemp() { return minTemp; }
    public void setMinTemp(double minTemp) { this.minTemp = minTemp; }
    
    @JsonProperty("max_temp")
    public double getMaxTemp() { return maxTemp; }
    public void setMaxTemp(double maxTemp) { this.maxTemp = maxTemp; }
    
    @JsonProperty("avg_temp")
    public double getAvgTemp() { return avgTemp; }
    public void setAvgTemp(double avgTemp) { this.avgTemp = avgTemp; }
    
    @JsonProperty("violation_count")
    public int getViolationCount() { return violationCount; }
    public void setViolationCount(int violationCount) { this.violationCount = violationCount; }
    
    public boolean isCompliant() { return compliant; }
    public void setCompliant(boolean compliant) { this.compliant = compliant; }
    
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    
    @Override
    public String toString() {
        return String.format("TemperatureEvent{batchId='%s', eventType='%s', temp=%.1f°C, compliant=%b}",
                           batchId, eventType, temperature, compliant);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TemperatureEvent that = (TemperatureEvent) o;
        return Double.compare(that.temperature, temperature) == 0 &&
               compliant == that.compliant &&
               timestamp == that.timestamp &&
               Objects.equals(batchId, that.batchId) &&
               Objects.equals(eventType, that.eventType);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(batchId, eventType, temperature, compliant, timestamp);
    }
}
