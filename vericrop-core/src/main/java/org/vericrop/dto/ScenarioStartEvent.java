package org.vericrop.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Event published when a scenario starts execution.
 * Topic: vericrop.scenarios.start
 */
public class ScenarioStartEvent {
    
    private String runId;
    private String scenarioName;
    private String farmerId;
    private String batchId;
    private long timestamp;
    private Map<String, Object> parameters;
    
    public ScenarioStartEvent() {
        this.timestamp = System.currentTimeMillis();
        this.parameters = new HashMap<>();
    }
    
    public ScenarioStartEvent(String runId, String scenarioName, String farmerId, String batchId) {
        this();
        this.runId = runId;
        this.scenarioName = scenarioName;
        this.farmerId = farmerId;
        this.batchId = batchId;
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
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public Map<String, Object> getParameters() {
        return parameters;
    }
    
    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters != null ? parameters : new HashMap<>();
    }
    
    @Override
    public String toString() {
        return String.format("ScenarioStartEvent{runId='%s', scenario='%s', farmerId='%s', batchId='%s'}",
                           runId, scenarioName, farmerId, batchId);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScenarioStartEvent that = (ScenarioStartEvent) o;
        return timestamp == that.timestamp &&
               Objects.equals(runId, that.runId) &&
               Objects.equals(scenarioName, that.scenarioName) &&
               Objects.equals(farmerId, that.farmerId) &&
               Objects.equals(batchId, that.batchId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(runId, scenarioName, farmerId, batchId, timestamp);
    }
}
