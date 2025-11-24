package org.vericrop.kafka.events;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Status event published by controllers to report progress.
 */
public class ControllerStatusEvent {
    
    @JsonProperty("orchestration_id")
    private String orchestrationId;
    
    @JsonProperty("controller_name")
    private String controllerName;
    
    @JsonProperty("instance_id")
    private String instanceId;
    
    @JsonProperty("status")
    private String status;  // started, completed, failed
    
    @JsonProperty("timestamp")
    private long timestamp;
    
    @JsonProperty("summary")
    private String summary;
    
    @JsonProperty("error_message")
    private String errorMessage;
    
    public ControllerStatusEvent() {
        this.instanceId = UUID.randomUUID().toString();
        this.timestamp = System.currentTimeMillis();
    }
    
    public ControllerStatusEvent(String orchestrationId, String controllerName, String status, String summary) {
        this();
        this.orchestrationId = orchestrationId;
        this.controllerName = controllerName;
        this.status = status;
        this.summary = summary;
    }
    
    // Getters and setters
    public String getOrchestrationId() {
        return orchestrationId;
    }
    
    public void setOrchestrationId(String orchestrationId) {
        this.orchestrationId = orchestrationId;
    }
    
    public String getControllerName() {
        return controllerName;
    }
    
    public void setControllerName(String controllerName) {
        this.controllerName = controllerName;
    }
    
    public String getInstanceId() {
        return instanceId;
    }
    
    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getSummary() {
        return summary;
    }
    
    public void setSummary(String summary) {
        this.summary = summary;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    @Override
    public String toString() {
        return String.format("ControllerStatusEvent{orchestrationId='%s', controllerName='%s', instanceId='%s', status='%s', timestamp=%d}",
                orchestrationId, controllerName, instanceId, status, timestamp);
    }
}
