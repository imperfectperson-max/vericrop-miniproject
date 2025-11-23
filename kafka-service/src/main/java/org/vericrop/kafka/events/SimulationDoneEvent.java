package org.vericrop.kafka.events;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Event to signal completion of a simulation component.
 * Published to simulations.done topic.
 */
public class SimulationDoneEvent {
    
    @JsonProperty("runId")
    private String runId;
    
    @JsonProperty("componentName")
    private String componentName;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("timestamp")
    private long timestamp;
    
    @JsonProperty("message")
    private String message;
    
    @JsonProperty("error")
    private String error;
    
    public SimulationDoneEvent() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public SimulationDoneEvent(String runId, String componentName, String status) {
        this.runId = runId;
        this.componentName = componentName;
        this.status = status;
        this.timestamp = System.currentTimeMillis();
    }
    
    public String getRunId() {
        return runId;
    }
    
    public void setRunId(String runId) {
        this.runId = runId;
    }
    
    public String getComponentName() {
        return componentName;
    }
    
    public void setComponentName(String componentName) {
        this.componentName = componentName;
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
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getError() {
        return error;
    }
    
    public void setError(String error) {
        this.error = error;
    }
    
    @Override
    public String toString() {
        return "SimulationDoneEvent{" +
                "runId='" + runId + '\'' +
                ", componentName='" + componentName + '\'' +
                ", status='" + status + '\'' +
                ", timestamp=" + timestamp +
                ", message='" + message + '\'' +
                ", error='" + error + '\'' +
                '}';
    }
}
