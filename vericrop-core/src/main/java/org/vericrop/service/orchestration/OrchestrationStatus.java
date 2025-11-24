package org.vericrop.service.orchestration;

import java.util.Map;

/**
 * External representation of orchestration status.
 * Immutable DTO for API responses.
 */
public class OrchestrationStatus {
    private final String correlationId;
    private final String batchId;
    private final String farmerId;
    private final Status status;
    private final long startTime;
    private final Map<StepType, StepStatus> steps;
    
    public OrchestrationStatus(String correlationId, String batchId, String farmerId, 
                              Status status, long startTime, Map<StepType, StepStatus> steps) {
        this.correlationId = correlationId;
        this.batchId = batchId;
        this.farmerId = farmerId;
        this.status = status;
        this.startTime = startTime;
        this.steps = steps;
    }
    
    public String getCorrelationId() {
        return correlationId;
    }
    
    public String getBatchId() {
        return batchId;
    }
    
    public String getFarmerId() {
        return farmerId;
    }
    
    public Status getStatus() {
        return status;
    }
    
    public long getStartTime() {
        return startTime;
    }
    
    public Map<StepType, StepStatus> getSteps() {
        return steps;
    }
    
    /**
     * Overall orchestration status
     */
    public enum Status {
        IN_FLIGHT,   // Orchestration started, some steps pending
        PARTIAL,     // Some steps complete, some pending
        COMPLETE     // All steps complete
    }
    
    /**
     * Status of an individual step in the orchestration
     */
    public static class StepStatus {
        private final boolean complete;
        private final long completionTime;
        private final StepResult result;
        
        public StepStatus(boolean complete, long completionTime, StepResult result) {
            this.complete = complete;
            this.completionTime = completionTime;
            this.result = result;
        }
        
        public boolean isComplete() {
            return complete;
        }
        
        public long getCompletionTime() {
            return completionTime;
        }
        
        public StepResult getResult() {
            return result;
        }
    }
}
