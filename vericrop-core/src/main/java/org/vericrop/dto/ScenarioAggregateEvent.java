package org.vericrop.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Event published when all scenarios in an orchestration run complete.
 * Contains aggregate results and status across all scenarios.
 * Topic: vericrop.scenarios.aggregate
 */
public class ScenarioAggregateEvent {
    
    private String runId;
    private String farmerId;
    private String batchId;
    private String status; // "COMPLETED", "PARTIAL_FAILURE", "FAILED"
    private String message;
    private long startTime;
    private long endTime;
    private int totalScenarios;
    private int successfulScenarios;
    private int failedScenarios;
    private Map<String, ScenarioSummary> scenarioSummaries;
    private Map<String, Object> aggregateResults;
    
    public ScenarioAggregateEvent() {
        this.scenarioSummaries = new HashMap<>();
        this.aggregateResults = new HashMap<>();
        this.endTime = System.currentTimeMillis();
    }
    
    public ScenarioAggregateEvent(String runId, String farmerId, String batchId, 
                                 String status, String message) {
        this();
        this.runId = runId;
        this.farmerId = farmerId;
        this.batchId = batchId;
        this.status = status;
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
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
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
    
    @JsonProperty("total_scenarios")
    public int getTotalScenarios() {
        return totalScenarios;
    }
    
    public void setTotalScenarios(int totalScenarios) {
        this.totalScenarios = totalScenarios;
    }
    
    @JsonProperty("successful_scenarios")
    public int getSuccessfulScenarios() {
        return successfulScenarios;
    }
    
    public void setSuccessfulScenarios(int successfulScenarios) {
        this.successfulScenarios = successfulScenarios;
    }
    
    @JsonProperty("failed_scenarios")
    public int getFailedScenarios() {
        return failedScenarios;
    }
    
    public void setFailedScenarios(int failedScenarios) {
        this.failedScenarios = failedScenarios;
    }
    
    @JsonProperty("scenario_summaries")
    public Map<String, ScenarioSummary> getScenarioSummaries() {
        return scenarioSummaries;
    }
    
    public void setScenarioSummaries(Map<String, ScenarioSummary> scenarioSummaries) {
        this.scenarioSummaries = scenarioSummaries != null ? scenarioSummaries : new HashMap<>();
    }
    
    @JsonProperty("aggregate_results")
    public Map<String, Object> getAggregateResults() {
        return aggregateResults;
    }
    
    public void setAggregateResults(Map<String, Object> aggregateResults) {
        this.aggregateResults = aggregateResults != null ? aggregateResults : new HashMap<>();
    }
    
    public long getTotalDurationMs() {
        return endTime - startTime;
    }
    
    @Override
    public String toString() {
        return String.format("ScenarioAggregateEvent{runId='%s', status='%s', total=%d, success=%d, failed=%d}",
                           runId, status, totalScenarios, successfulScenarios, failedScenarios);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScenarioAggregateEvent that = (ScenarioAggregateEvent) o;
        return startTime == that.startTime &&
               endTime == that.endTime &&
               totalScenarios == that.totalScenarios &&
               successfulScenarios == that.successfulScenarios &&
               failedScenarios == that.failedScenarios &&
               Objects.equals(runId, that.runId) &&
               Objects.equals(status, that.status);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(runId, status, startTime, endTime, totalScenarios, 
                          successfulScenarios, failedScenarios);
    }
    
    /**
     * Summary of an individual scenario within the aggregate.
     */
    public static class ScenarioSummary {
        private String scenarioName;
        private boolean success;
        private String message;
        private long durationMs;
        
        public ScenarioSummary() {
        }
        
        public ScenarioSummary(String scenarioName, boolean success, String message, long durationMs) {
            this.scenarioName = scenarioName;
            this.success = success;
            this.message = message;
            this.durationMs = durationMs;
        }
        
        @JsonProperty("scenario_name")
        public String getScenarioName() {
            return scenarioName;
        }
        
        public void setScenarioName(String scenarioName) {
            this.scenarioName = scenarioName;
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
        
        @JsonProperty("duration_ms")
        public long getDurationMs() {
            return durationMs;
        }
        
        public void setDurationMs(long durationMs) {
            this.durationMs = durationMs;
        }
        
        @Override
        public String toString() {
            return String.format("ScenarioSummary{name='%s', success=%s, duration=%dms}", 
                               scenarioName, success, durationMs);
        }
    }
}
