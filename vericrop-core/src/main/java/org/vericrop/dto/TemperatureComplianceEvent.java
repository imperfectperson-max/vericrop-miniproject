package org.vericrop.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;

/**
 * Event published for temperature compliance monitoring
 */
public class TemperatureComplianceEvent {
    private String batchId;
    private long timestamp;
    private double temperature;
    private boolean compliant;
    private String scenarioId;
    private String details;
    
    // For summary events
    private boolean isSummary;
    private List<Violation> violations;
    
    public TemperatureComplianceEvent() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public TemperatureComplianceEvent(String batchId, double temperature, boolean compliant, 
                                       String scenarioId, String details) {
        this();
        this.batchId = batchId;
        this.temperature = temperature;
        this.compliant = compliant;
        this.scenarioId = scenarioId;
        this.details = details;
        this.isSummary = false;
    }
    
    // Getters and setters
    @JsonProperty("batch_id")
    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }
    
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    
    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
    
    public boolean isCompliant() { return compliant; }
    public void setCompliant(boolean compliant) { this.compliant = compliant; }
    
    @JsonProperty("scenario_id")
    public String getScenarioId() { return scenarioId; }
    public void setScenarioId(String scenarioId) { this.scenarioId = scenarioId; }
    
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    
    @JsonProperty("is_summary")
    public boolean isSummary() { return isSummary; }
    public void setSummary(boolean summary) { isSummary = summary; }
    
    public List<Violation> getViolations() { return violations; }
    public void setViolations(List<Violation> violations) { this.violations = violations; }
    
    @Override
    public String toString() {
        if (isSummary) {
            return String.format("TemperatureComplianceSummary{batchId='%s', compliant=%b, violations=%d}",
                    batchId, compliant, violations != null ? violations.size() : 0);
        }
        return String.format("TemperatureComplianceEvent{batchId='%s', temp=%.1f°C, compliant=%b}",
                batchId, temperature, compliant);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TemperatureComplianceEvent that = (TemperatureComplianceEvent) o;
        return timestamp == that.timestamp &&
                Double.compare(that.temperature, temperature) == 0 &&
                compliant == that.compliant &&
                Objects.equals(batchId, that.batchId) &&
                Objects.equals(scenarioId, that.scenarioId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(batchId, timestamp, temperature, compliant, scenarioId);
    }
    
    /**
     * Represents a temperature violation
     */
    public static class Violation {
        private long timestamp;
        private double temperature;
        private int durationSeconds;
        private String description;
        
        public Violation() {}
        
        public Violation(long timestamp, double temperature, int durationSeconds, String description) {
            this.timestamp = timestamp;
            this.temperature = temperature;
            this.durationSeconds = durationSeconds;
            this.description = description;
        }
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        
        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }
        
        @JsonProperty("duration_seconds")
        public int getDurationSeconds() { return durationSeconds; }
        public void setDurationSeconds(int durationSeconds) { this.durationSeconds = durationSeconds; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
}
