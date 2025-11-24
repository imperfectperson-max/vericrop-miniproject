package org.vericrop.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Event published when a scenario completes execution (success or failure).
 * Topic: vericrop.scenarios.complete
 */
public class ScenarioCompleteEvent {
    
    private String runId;
    private String scenarioName;
    private String farmerId;
    private String batchId;
    private boolean success;
    private String message;
    private long startTime;
    private long endTime;
    private Map<String, Object> results;
    
    public ScenarioCompleteEvent() {
        this.results = new HashMap<>();
        this.endTime = System.currentTimeMillis();
    }
    
    public ScenarioCompleteEvent(String runId, String scenarioName, String farmerId, 
                                String batchId, boolean success, String message) {
        this();
        this.runId = runId;
        this.scenarioName = scenarioName;
        this.farmerId = farmerId;
        this.batchId = batchId;
        this.success = success;
        this.message = message;
    }
    
    // Getters and Setters
    @JsonProperty("run_id")
    public String getRunId() {
        return runId;
    }
    
    public void setRunId(String runId) {
        this.runId = runId;
    }
    
    @JsonProperty("scenario_name")
    public String getScenarioName() {
        return scenarioName;
    }
    
    public void setScenarioName(String scenarioName) {
        this.scenarioName = scenarioName;
    }
    
    @JsonProperty("farmer_id")
    public String getFarmerId() {
        return farmerId;
    }
    
    public void setFarmerId(String farmerId) {
        this.farmerId = farmerId;
    }
    
    @JsonProperty("batch_id")
    public String getBatchId() {
        return batchId;
    }
    
    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    @JsonProperty("start_time")
    public long getStartTime() {
        return startTime;
    }
    
    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }
    
    @JsonProperty("end_time")
    public long getEndTime() {
        return endTime;
    }
    
    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }
    
    public Map<String, Object> getResults() {
        return results;
    }
    
    public void setResults(Map<String, Object> results) {
        this.results = results != null ? results : new HashMap<>();
    }
    
    public long getDurationMs() {
        return endTime - startTime;
    }
    
    @Override
    public String toString() {
        return String.format("ScenarioCompleteEvent{runId='%s', scenario='%s', success=%s, message='%s'}",
                           runId, scenarioName, success, message);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScenarioCompleteEvent that = (ScenarioCompleteEvent) o;
        return success == that.success &&
               startTime == that.startTime &&
               endTime == that.endTime &&
               Objects.equals(runId, that.runId) &&
               Objects.equals(scenarioName, that.scenarioName) &&
               Objects.equals(farmerId, that.farmerId) &&
               Objects.equals(batchId, that.batchId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(runId, scenarioName, farmerId, batchId, success, startTime, endTime);
    }
}
