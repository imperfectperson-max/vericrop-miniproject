package org.vericrop.service.orchestration;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Result object containing per-scenario execution status and aggregated results.
 */
public class ScenarioOrchestrationResult {
    private final String requestId;
    private final Instant startTime;
    private final Instant endTime;
    private final Map<ScenarioGroup, ScenarioExecutionStatus> scenarioStatuses;
    private final boolean overallSuccess;
    private final String message;
    
    public ScenarioOrchestrationResult(
            String requestId,
            Instant startTime,
            Instant endTime,
            Map<ScenarioGroup, ScenarioExecutionStatus> scenarioStatuses) {
        this.requestId = requestId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.scenarioStatuses = scenarioStatuses != null ? scenarioStatuses : new HashMap<>();
        
        // Calculate overall success - all scenarios must succeed
        this.overallSuccess = this.scenarioStatuses.values().stream()
                .allMatch(status -> status.getStatus() == ExecutionStatus.SUCCESS);
        
        // Generate summary message
        long successCount = this.scenarioStatuses.values().stream()
                .filter(status -> status.getStatus() == ExecutionStatus.SUCCESS)
                .count();
        long failureCount = this.scenarioStatuses.size() - successCount;
        
        if (failureCount == 0) {
            this.message = String.format("All %d scenario groups executed successfully", successCount);
        } else {
            this.message = String.format("%d succeeded, %d failed out of %d scenario groups",
                    successCount, failureCount, this.scenarioStatuses.size());
        }
    }
    
    public String getRequestId() {
        return requestId;
    }
    
    public Instant getStartTime() {
        return startTime;
    }
    
    public Instant getEndTime() {
        return endTime;
    }
    
    public Map<ScenarioGroup, ScenarioExecutionStatus> getScenarioStatuses() {
        return scenarioStatuses;
    }
    
    public boolean isOverallSuccess() {
        return overallSuccess;
    }
    
    public String getMessage() {
        return message;
    }
    
    public long getDurationMs() {
        if (startTime == null || endTime == null) {
            return 0;
        }
        return endTime.toEpochMilli() - startTime.toEpochMilli();
    }
    
    /**
     * Individual scenario execution status.
     */
    public static class ScenarioExecutionStatus {
        private final ScenarioGroup scenarioGroup;
        private final ExecutionStatus status;
        private final String message;
        private final Instant timestamp;
        private final String errorDetails;
        
        public ScenarioExecutionStatus(
                ScenarioGroup scenarioGroup,
                ExecutionStatus status,
                String message,
                Instant timestamp,
                String errorDetails) {
            this.scenarioGroup = scenarioGroup;
            this.status = status;
            this.message = message;
            this.timestamp = timestamp;
            this.errorDetails = errorDetails;
        }
        
        public ScenarioGroup getScenarioGroup() {
            return scenarioGroup;
        }
        
        public ExecutionStatus getStatus() {
            return status;
        }
        
        public String getMessage() {
            return message;
        }
        
        public Instant getTimestamp() {
            return timestamp;
        }
        
        public String getErrorDetails() {
            return errorDetails;
        }
    }
    
    /**
     * Execution status enum.
     */
    public enum ExecutionStatus {
        SUCCESS,
        FAILURE,
        TIMEOUT,
        PARTIAL
    }
}
