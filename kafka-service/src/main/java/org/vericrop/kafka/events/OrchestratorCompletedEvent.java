package org.vericrop.kafka.events;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Event published when orchestration is complete.
 */
public class OrchestratorCompletedEvent {
    
    @JsonProperty("orchestration_id")
    private String orchestrationId;
    
    @JsonProperty("timestamp")
    private long timestamp;
    
    @JsonProperty("success")
    private boolean success;
    
    @JsonProperty("total_controllers")
    private int totalControllers;
    
    @JsonProperty("completed_controllers")
    private int completedControllers;
    
    @JsonProperty("failed_controllers")
    private int failedControllers;
    
    @JsonProperty("summary")
    private String summary;
    
    @JsonProperty("controller_results")
    private Map<String, String> controllerResults;
    
    public OrchestratorCompletedEvent() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public OrchestratorCompletedEvent(String orchestrationId, boolean success, int totalControllers, 
                                     int completedControllers, int failedControllers, String summary,
                                     Map<String, String> controllerResults) {
        this();
        this.orchestrationId = orchestrationId;
        this.success = success;
        this.totalControllers = totalControllers;
        this.completedControllers = completedControllers;
        this.failedControllers = failedControllers;
        this.summary = summary;
        this.controllerResults = controllerResults;
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
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public int getTotalControllers() {
        return totalControllers;
    }
    
    public void setTotalControllers(int totalControllers) {
        this.totalControllers = totalControllers;
    }
    
    public int getCompletedControllers() {
        return completedControllers;
    }
    
    public void setCompletedControllers(int completedControllers) {
        this.completedControllers = completedControllers;
    }
    
    public int getFailedControllers() {
        return failedControllers;
    }
    
    public void setFailedControllers(int failedControllers) {
        this.failedControllers = failedControllers;
    }
    
    public String getSummary() {
        return summary;
    }
    
    public void setSummary(String summary) {
        this.summary = summary;
    }
    
    public Map<String, String> getControllerResults() {
        return controllerResults;
    }
    
    public void setControllerResults(Map<String, String> controllerResults) {
        this.controllerResults = controllerResults;
    }
    
    @Override
    public String toString() {
        return String.format("OrchestratorCompletedEvent{orchestrationId='%s', success=%b, totalControllers=%d, completedControllers=%d, failedControllers=%d, timestamp=%d}",
                orchestrationId, success, totalControllers, completedControllers, failedControllers, timestamp);
    }
}
