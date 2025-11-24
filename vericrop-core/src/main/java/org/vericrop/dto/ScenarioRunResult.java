package org.vericrop.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Result object for scenario orchestration execution.
 * Contains the run ID, status, and results from each scenario.
 */
public class ScenarioRunResult {
    
    private String runId;
    private String status; // "ACCEPTED", "RUNNING", "COMPLETED", "FAILED"
    private String message;
    private long startTime;
    private Long endTime;
    
    private Map<String, ScenarioStatus> scenarioStatuses;
    private Map<String, Object> aggregateResults;
    
    public ScenarioRunResult() {
        this.scenarioStatuses = new HashMap<>();
        this.aggregateResults = new HashMap<>();
        this.startTime = System.currentTimeMillis();
    }
    
    public ScenarioRunResult(String runId, String status, String message) {
        this();
        this.runId = runId;
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
    public Long getEndTime() {
        return endTime;
    }
    
    public void setEndTime(Long endTime) {
        this.endTime = endTime;
    }
    
    @JsonProperty("scenario_statuses")
    public Map<String, ScenarioStatus> getScenarioStatuses() {
        return scenarioStatuses;
    }
    
    public void setScenarioStatuses(Map<String, ScenarioStatus> scenarioStatuses) {
        this.scenarioStatuses = scenarioStatuses != null ? scenarioStatuses : new HashMap<>();
    }
    
    @JsonProperty("aggregate_results")
    public Map<String, Object> getAggregateResults() {
        return aggregateResults;
    }
    
    public void setAggregateResults(Map<String, Object> aggregateResults) {
        this.aggregateResults = aggregateResults != null ? aggregateResults : new HashMap<>();
    }
    
    /**
     * Add a scenario status to the result.
     */
    public void addScenarioStatus(String scenarioName, ScenarioStatus status) {
        this.scenarioStatuses.put(scenarioName, status);
    }
    
    /**
     * Check if all scenarios have completed (successfully or with failure).
     */
    public boolean isAllScenariosCompleted() {
        return scenarioStatuses.values().stream()
                .allMatch(s -> "COMPLETED".equals(s.getStatus()) || "FAILED".equals(s.getStatus()));
    }
    
    /**
     * Check if any scenario has failed.
     */
    public boolean hasAnyFailure() {
        return scenarioStatuses.values().stream()
                .anyMatch(s -> "FAILED".equals(s.getStatus()));
    }
    
    @Override
    public String toString() {
        return String.format("ScenarioRunResult{runId='%s', status='%s', message='%s', scenarioCount=%d}",
                           runId, status, message, scenarioStatuses.size());
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScenarioRunResult that = (ScenarioRunResult) o;
        return startTime == that.startTime &&
               Objects.equals(runId, that.runId) &&
               Objects.equals(status, that.status) &&
               Objects.equals(endTime, that.endTime);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(runId, status, startTime, endTime);
    }
    
    /**
     * Individual scenario status within an orchestration run.
     */
    public static class ScenarioStatus {
        private String status; // "STARTED", "RUNNING", "COMPLETED", "FAILED"
        private String message;
        private long startTime;
        private Long endTime;
        private Map<String, Object> results;
        
        public ScenarioStatus() {
            this.results = new HashMap<>();
            this.startTime = System.currentTimeMillis();
        }
        
        public ScenarioStatus(String status, String message) {
            this();
            this.status = status;
            this.message = message;
        }
        
        // Getters and Setters
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
        public Long getEndTime() {
            return endTime;
        }
        
        public void setEndTime(Long endTime) {
            this.endTime = endTime;
        }
        
        public Map<String, Object> getResults() {
            return results;
        }
        
        public void setResults(Map<String, Object> results) {
            this.results = results != null ? results : new HashMap<>();
        }
        
        @Override
        public String toString() {
            return String.format("ScenarioStatus{status='%s', message='%s'}", status, message);
        }
    }
}
