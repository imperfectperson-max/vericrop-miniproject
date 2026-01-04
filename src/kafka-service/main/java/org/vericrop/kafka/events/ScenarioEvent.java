package org.vericrop.kafka.events;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Event for scenario execution lifecycle (started, completed, failed).
 * Published when scenarios are triggered, running, or completed.
 */
public class ScenarioEvent {
    private String executionId;
    private String eventType; // "STARTED", "RUNNING", "COMPLETED", "FAILED"
    private String scenarioName;
    private String farmerId;
    private String batchId;
    private long timestamp;
    private Map<String, Object> metadata;
    
    public ScenarioEvent() {
        this.timestamp = System.currentTimeMillis();
        this.metadata = new HashMap<>();
    }
    
    public ScenarioEvent(String executionId, String eventType, String scenarioName, 
                        String farmerId, String batchId) {
        this();
        this.executionId = executionId;
        this.eventType = eventType;
        this.scenarioName = scenarioName;
        this.farmerId = farmerId;
        this.batchId = batchId;
    }
    
    // Getters and Setters
    @JsonProperty("execution_id")
    public String getExecutionId() { return executionId; }
    public void setExecutionId(String executionId) { this.executionId = executionId; }
    
    @JsonProperty("event_type")
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    
    @JsonProperty("scenario_name")
    public String getScenarioName() { return scenarioName; }
    public void setScenarioName(String scenarioName) { this.scenarioName = scenarioName; }
    
    @JsonProperty("farmer_id")
    public String getFarmerId() { return farmerId; }
    public void setFarmerId(String farmerId) { this.farmerId = farmerId; }
    
    @JsonProperty("batch_id")
    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }
    
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    
    public void addMetadata(String key, Object value) {
        this.metadata.put(key, value);
    }
    
    @Override
    public String toString() {
        return String.format("ScenarioEvent{executionId='%s', eventType='%s', scenario='%s', farmerId='%s', batchId='%s'}",
                           executionId, eventType, scenarioName, farmerId, batchId);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScenarioEvent that = (ScenarioEvent) o;
        return timestamp == that.timestamp &&
               Objects.equals(executionId, that.executionId) &&
               Objects.equals(eventType, that.eventType) &&
               Objects.equals(scenarioName, that.scenarioName) &&
               Objects.equals(farmerId, that.farmerId) &&
               Objects.equals(batchId, that.batchId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(executionId, eventType, scenarioName, farmerId, batchId, timestamp);
    }
}
