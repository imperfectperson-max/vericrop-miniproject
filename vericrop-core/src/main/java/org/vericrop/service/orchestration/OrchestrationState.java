package org.vericrop.service.orchestration;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Internal state tracker for an orchestration.
 * Thread-safe for concurrent step completion.
 */
class OrchestrationState {
    private final String correlationId;
    private final String batchId;
    private final String farmerId;
    private final long startTime;
    private final Map<StepType, StepInfo> steps;
    private volatile OrchestrationStatus.Status status;
    
    OrchestrationState(String correlationId, String batchId, String farmerId, List<StepType> stepTypes) {
        this.correlationId = correlationId;
        this.batchId = batchId;
        this.farmerId = farmerId;
        this.startTime = System.currentTimeMillis();
        this.status = OrchestrationStatus.Status.IN_FLIGHT;
        
        // Initialize all steps as pending
        this.steps = new ConcurrentHashMap<>();
        for (StepType stepType : stepTypes) {
            steps.put(stepType, new StepInfo(stepType));
        }
    }
    
    /**
     * Mark a step as complete with result data
     */
    void completeStep(StepType stepType, StepResult result) {
        StepInfo stepInfo = steps.get(stepType);
        if (stepInfo != null) {
            stepInfo.complete(result);
            
            // Update overall status to PARTIAL if at least one step complete but not all
            if (getCompletedCount() > 0 && !isComplete()) {
                status = OrchestrationStatus.Status.PARTIAL;
            }
        }
    }
    
    /**
     * Check if all steps are complete
     */
    boolean isComplete() {
        return steps.values().stream().allMatch(StepInfo::isComplete);
    }
    
    /**
     * Get count of completed steps
     */
    int getCompletedCount() {
        return (int) steps.values().stream().filter(StepInfo::isComplete).count();
    }
    
    /**
     * Get total count of steps
     */
    int getTotalCount() {
        return steps.size();
    }
    
    /**
     * Get all steps as unmodifiable map
     */
    Map<StepType, StepInfo> getSteps() {
        return Collections.unmodifiableMap(steps);
    }
    
    String getCorrelationId() {
        return correlationId;
    }
    
    String getBatchId() {
        return batchId;
    }
    
    String getFarmerId() {
        return farmerId;
    }
    
    long getStartTime() {
        return startTime;
    }
    
    OrchestrationStatus.Status getStatus() {
        return status;
    }
    
    void setStatus(OrchestrationStatus.Status status) {
        this.status = status;
    }
    
    /**
     * Convert to external OrchestrationStatus DTO
     */
    OrchestrationStatus toStatus() {
        Map<StepType, OrchestrationStatus.StepStatus> stepStatuses = new HashMap<>();
        
        for (Map.Entry<StepType, StepInfo> entry : steps.entrySet()) {
            StepInfo info = entry.getValue();
            stepStatuses.put(entry.getKey(), new OrchestrationStatus.StepStatus(
                info.isComplete(),
                info.getCompletionTime(),
                info.getResult()
            ));
        }
        
        return new OrchestrationStatus(
            correlationId,
            batchId,
            farmerId,
            status,
            startTime,
            stepStatuses
        );
    }
    
    /**
     * Internal tracking for individual step
     */
    static class StepInfo {
        private final StepType stepType;
        private volatile boolean complete;
        private volatile long completionTime;
        private volatile StepResult result;
        
        StepInfo(StepType stepType) {
            this.stepType = stepType;
            this.complete = false;
            this.completionTime = 0;
        }
        
        void complete(StepResult result) {
            this.complete = true;
            this.completionTime = System.currentTimeMillis();
            this.result = result;
        }
        
        boolean isComplete() {
            return complete;
        }
        
        long getCompletionTime() {
            return completionTime;
        }
        
        StepResult getResult() {
            return result;
        }
        
        StepType getStepType() {
            return stepType;
        }
    }
}
