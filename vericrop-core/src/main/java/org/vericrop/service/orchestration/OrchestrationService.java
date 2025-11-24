package org.vericrop.service.orchestration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Orchestrates concurrent execution of multiple functional areas in the supply chain.
 * Tracks progress of scenarios, delivery, map, temperature, supplierCompliance, and simulations.
 */
public class OrchestrationService {
    private static final Logger logger = LoggerFactory.getLogger(OrchestrationService.class);
    
    // In-memory storage for orchestration state (TODO: migrate to persistent storage for production)
    private final ConcurrentHashMap<String, OrchestrationState> orchestrations = new ConcurrentHashMap<>();
    
    /**
     * Start a new orchestration for a composite request
     * 
     * @param request The composite request containing all necessary information
     * @return A unique correlationId/orchestrationId for tracking
     */
    public String startOrchestration(CompositeRequest request) {
        String correlationId = generateCorrelationId();
        
        // Initialize orchestration state with all expected steps
        OrchestrationState state = new OrchestrationState(
            correlationId,
            request.getBatchId(),
            request.getFarmerId(),
            Arrays.asList(
                StepType.SCENARIOS,
                StepType.DELIVERY,
                StepType.MAP,
                StepType.TEMPERATURE,
                StepType.SUPPLIER_COMPLIANCE,
                StepType.SIMULATIONS
            )
        );
        
        orchestrations.put(correlationId, state);
        
        logger.info("Started orchestration {} for batch {} with {} steps", 
            correlationId, request.getBatchId(), state.getSteps().size());
        
        return correlationId;
    }
    
    /**
     * Mark a step as complete in the orchestration
     * 
     * @param correlationId The orchestration identifier
     * @param stepType The type of step being completed
     * @param result The result data from the step
     */
    public void markStepComplete(String correlationId, StepType stepType, StepResult result) {
        OrchestrationState state = orchestrations.get(correlationId);
        
        if (state == null) {
            logger.warn("Attempted to mark step {} complete for unknown orchestration {}", 
                stepType, correlationId);
            return;
        }
        
        state.completeStep(stepType, result);
        
        logger.info("Marked step {} complete for orchestration {}. Progress: {}/{}", 
            stepType, correlationId, state.getCompletedCount(), state.getTotalCount());
        
        // Check if orchestration is fully complete
        if (state.isComplete()) {
            state.setStatus(OrchestrationStatus.Status.COMPLETE);
            logger.info("Orchestration {} is now complete", correlationId);
        }
    }
    
    /**
     * Get the current status of an orchestration
     * 
     * @param correlationId The orchestration identifier
     * @return The current orchestration status, or null if not found
     */
    public OrchestrationStatus getStatus(String correlationId) {
        OrchestrationState state = orchestrations.get(correlationId);
        
        if (state == null) {
            logger.warn("Status requested for unknown orchestration {}", correlationId);
            return null;
        }
        
        return state.toStatus();
    }
    
    /**
     * Get all active orchestrations (for monitoring/debugging)
     * 
     * @return List of all orchestration statuses
     */
    public List<OrchestrationStatus> getAllOrchestrations() {
        List<OrchestrationStatus> statuses = new ArrayList<>();
        
        for (OrchestrationState state : orchestrations.values()) {
            statuses.add(state.toStatus());
        }
        
        return statuses;
    }
    
    /**
     * Clean up completed orchestrations older than the specified age
     * 
     * @param maxAgeMs Maximum age in milliseconds
     * @return Number of orchestrations cleaned up
     */
    public int cleanupOldOrchestrations(long maxAgeMs) {
        long cutoffTime = System.currentTimeMillis() - maxAgeMs;
        AtomicInteger cleaned = new AtomicInteger(0);
        
        orchestrations.entrySet().removeIf(entry -> {
            OrchestrationState state = entry.getValue();
            if (state.isComplete() && state.getStartTime() < cutoffTime) {
                cleaned.incrementAndGet();
                return true;
            }
            return false;
        });
        
        if (cleaned.get() > 0) {
            logger.info("Cleaned up {} old completed orchestrations", cleaned.get());
        }
        
        return cleaned.get();
    }
    
    /**
     * Generate a unique correlation ID
     */
    private String generateCorrelationId() {
        return "ORCH-" + UUID.randomUUID().toString();
    }
}
