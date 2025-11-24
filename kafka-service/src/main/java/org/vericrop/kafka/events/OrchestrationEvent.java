package org.vericrop.kafka.events;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

/**
 * Event for orchestration lifecycle (started, progress, completed).
 * Used to coordinate concurrent execution of multiple functional areas.
 */
public class OrchestrationEvent {
    private String correlationId;
    private String batchId;
    private String farmerId;
    private String stepType;  // scenarios, delivery, map, temperature, supplierCompliance, simulations
    private String eventType; // "STARTED", "IN_PROGRESS", "COMPLETED", "FAILED"
    private long timestamp;
    private Map<String, Object> metadata;
    
    public OrchestrationEvent() {
        this.timestamp = System.currentTimeMillis();
        this.metadata = new HashMap<>();
    }
    
    public OrchestrationEvent(String correlationId, String batchId, String farmerId, 
                              String stepType, String eventType) {
        this();
        this.correlationId = correlationId;
        this.batchId = batchId;
        this.farmerId = farmerId;
        this.stepType = stepType;
        this.eventType = eventType;
    }
    
    @JsonProperty("correlation_id")
    public String getCorrelationId() {
        return correlationId;
    }
    
    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }
    
    @JsonProperty("batch_id")
    public String getBatchId() {
        return batchId;
    }
    
    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }
    
    @JsonProperty("farmer_id")
    public String getFarmerId() {
        return farmerId;
    }
    
    public void setFarmerId(String farmerId) {
        this.farmerId = farmerId;
    }
    
    @JsonProperty("step_type")
    public String getStepType() {
        return stepType;
    }
    
    public void setStepType(String stepType) {
        this.stepType = stepType;
    }
    
    @JsonProperty("event_type")
    public String getEventType() {
        return eventType;
    }
    
    public void setEventType(String eventType) {
        this.eventType = eventType;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
    
    public void addMetadata(String key, Object value) {
        this.metadata.put(key, value);
    }
    
    @Override
    public String toString() {
        return String.format("OrchestrationEvent{correlationId='%s', batchId='%s', stepType='%s', eventType='%s'}",
                           correlationId, batchId, stepType, eventType);
    }
}
