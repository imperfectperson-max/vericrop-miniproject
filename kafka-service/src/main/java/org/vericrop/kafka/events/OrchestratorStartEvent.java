package org.vericrop.kafka.events;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

/**
 * Event to start orchestration of multiple controllers.
 */
public class OrchestratorStartEvent {
    
    @JsonProperty("orchestration_id")
    private String orchestrationId;
    
    @JsonProperty("timestamp")
    private long timestamp;
    
    @JsonProperty("controllers")
    private List<String> controllers;
    
    @JsonProperty("parameters")
    private java.util.Map<String, Object> parameters;
    
    public OrchestratorStartEvent() {
        this.orchestrationId = UUID.randomUUID().toString();
        this.timestamp = System.currentTimeMillis();
    }
    
    public OrchestratorStartEvent(List<String> controllers, java.util.Map<String, Object> parameters) {
        this();
        this.controllers = controllers;
        this.parameters = parameters;
    }
    
    // Getters and setters
    public String getOrchestrationId() {
        return orchestrationId;
    }
    
    public void setOrchestrationId(String orchestrationId) {
        this.orchestrationId = orchestrationId;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public List<String> getControllers() {
        return controllers;
    }
    
    public void setControllers(List<String> controllers) {
        this.controllers = controllers;
    }
    
    public java.util.Map<String, Object> getParameters() {
        return parameters;
    }
    
    public void setParameters(java.util.Map<String, Object> parameters) {
        this.parameters = parameters;
    }
    
    @Override
    public String toString() {
        return String.format("OrchestratorStartEvent{orchestrationId='%s', timestamp=%d, controllers=%s}",
                orchestrationId, timestamp, controllers);
    }
}
