package org.vericrop.service.orchestrator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

/**
 * Orchestrator component that coordinates multiple controllers to run in parallel.
 * Tracks controller status, aggregates results, and emits completion events.
 * 
 * This orchestrator is independent and testable, managing controller lifecycle
 * through Kafka-based event-driven coordination.
 */
public class Orchestrator {
    
    private static final Logger logger = LoggerFactory.getLogger(Orchestrator.class);
    private static final long STATUS_CHECK_INTERVAL_MS = 5000; // 5 seconds
    private static final long DEFAULT_TIMEOUT_MS = 300000; // 5 minutes
    
    private final ExecutorService executorService;
    private final ScheduledExecutorService statusMonitor;
    private final ConcurrentHashMap<String, OrchestrationContext> activeOrchestrations;
    private final OrchestrationEventPublisher eventPublisher;
    
    /**
     * Context for a running orchestration.
     */
    public static class OrchestrationContext {
        private final String orchestrationId;
        private final List<String> controllers;
        private final long startTime;
        private final long timeoutMs;
        private final Map<String, ControllerState> controllerStates;
        private volatile boolean completed;
        
        public OrchestrationContext(String orchestrationId, List<String> controllers, long timeoutMs) {
            this.orchestrationId = orchestrationId;
            this.controllers = new ArrayList<>(controllers);
            this.startTime = System.currentTimeMillis();
            this.timeoutMs = timeoutMs;
            this.controllerStates = new ConcurrentHashMap<>();
            this.completed = false;
            
            // Initialize controller states
            for (String controller : controllers) {
                controllerStates.put(controller, new ControllerState(controller));
            }
        }
        
        public String getOrchestrationId() { return orchestrationId; }
        public List<String> getControllers() { return controllers; }
        public long getStartTime() { return startTime; }
        public long getTimeoutMs() { return timeoutMs; }
        public Map<String, ControllerState> getControllerStates() { return controllerStates; }
        public boolean isCompleted() { return completed; }
        public void setCompleted(boolean completed) { this.completed = completed; }
        
        public boolean isTimedOut() {
            return System.currentTimeMillis() - startTime > timeoutMs;
        }
        
        public boolean allControllersCompleted() {
            return controllerStates.values().stream()
                    .allMatch(state -> "completed".equals(state.getStatus()) || "failed".equals(state.getStatus()));
        }
        
        public int getCompletedCount() {
            return (int) controllerStates.values().stream()
                    .filter(state -> "completed".equals(state.getStatus()))
                    .count();
        }
        
        public int getFailedCount() {
            return (int) controllerStates.values().stream()
                    .filter(state -> "failed".equals(state.getStatus()))
                    .count();
        }
    }
    
    /**
     * State of an individual controller.
     */
    public static class ControllerState {
        private final String controllerName;
        private volatile String status; // started, completed, failed
        private volatile String instanceId;
        private volatile long lastUpdateTime;
        private volatile String summary;
        private volatile String errorMessage;
        
        public ControllerState(String controllerName) {
            this.controllerName = controllerName;
            this.status = "pending";
            this.lastUpdateTime = System.currentTimeMillis();
        }
        
        public String getControllerName() { return controllerName; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getInstanceId() { return instanceId; }
        public void setInstanceId(String instanceId) { this.instanceId = instanceId; }
        public long getLastUpdateTime() { return lastUpdateTime; }
        public void setLastUpdateTime(long lastUpdateTime) { this.lastUpdateTime = lastUpdateTime; }
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }
    
    /**
     * Interface for publishing orchestration events (Kafka integration).
     */
    public interface OrchestrationEventPublisher {
        void publishControllerCommand(String orchestrationId, String controllerName, String command, Map<String, Object> parameters);
        void publishCompletedEvent(String orchestrationId, boolean success, Map<String, String> results, String summary);
    }
    
    /**
     * Constructor with event publisher.
     */
    public Orchestrator(OrchestrationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
        this.executorService = Executors.newCachedThreadPool();
        this.statusMonitor = Executors.newScheduledThreadPool(1);
        this.activeOrchestrations = new ConcurrentHashMap<>();
        
        // Start periodic status monitoring
        statusMonitor.scheduleAtFixedRate(
                this::checkOrchestrationStatus,
                STATUS_CHECK_INTERVAL_MS,
                STATUS_CHECK_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );
        
        logger.info("Orchestrator initialized");
    }
    
    /**
     * Start orchestration for a list of controllers.
     * 
     * @param orchestrationId Unique identifier for this orchestration
     * @param controllers List of controller names to orchestrate
     * @param parameters Optional parameters to pass to controllers
     * @param timeoutMs Timeout in milliseconds (0 for default)
     * @return OrchestrationContext for tracking
     */
    public OrchestrationContext startOrchestration(String orchestrationId, List<String> controllers, 
                                                   Map<String, Object> parameters, long timeoutMs) {
        if (controllers == null || controllers.isEmpty()) {
            throw new IllegalArgumentException("Controllers list cannot be null or empty");
        }
        
        if (activeOrchestrations.containsKey(orchestrationId)) {
            logger.warn("Orchestration already exists: {}", orchestrationId);
            return activeOrchestrations.get(orchestrationId);
        }
        
        long actualTimeout = timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS;
        OrchestrationContext context = new OrchestrationContext(orchestrationId, controllers, actualTimeout);
        activeOrchestrations.put(orchestrationId, context);
        
        logger.info("Starting orchestration: {} with {} controllers", orchestrationId, controllers.size());
        
        // Send start commands to all controllers asynchronously
        executorService.submit(() -> {
            for (String controller : controllers) {
                try {
                    logger.info("Sending start command to controller: {}", controller);
                    eventPublisher.publishControllerCommand(orchestrationId, controller, "start", parameters);
                    
                    // Update controller state to starting
                    ControllerState state = context.getControllerStates().get(controller);
                    if (state != null) {
                        state.setStatus("starting");
                        state.setLastUpdateTime(System.currentTimeMillis());
                    }
                    
                } catch (Exception e) {
                    logger.error("Failed to send start command to controller: {}", controller, e);
                    ControllerState state = context.getControllerStates().get(controller);
                    if (state != null) {
                        state.setStatus("failed");
                        state.setErrorMessage("Failed to send start command: " + e.getMessage());
                    }
                }
            }
        });
        
        return context;
    }
    
    /**
     * Handle controller status update.
     * 
     * @param orchestrationId Orchestration ID
     * @param controllerName Controller name
     * @param instanceId Controller instance ID
     * @param status Status (started, completed, failed)
     * @param summary Summary message
     * @param errorMessage Error message (if failed)
     */
    public void handleControllerStatus(String orchestrationId, String controllerName, String instanceId,
                                       String status, String summary, String errorMessage) {
        OrchestrationContext context = activeOrchestrations.get(orchestrationId);
        if (context == null) {
            logger.warn("Received status for unknown orchestration: {}", orchestrationId);
            return;
        }
        
        ControllerState state = context.getControllerStates().get(controllerName);
        if (state == null) {
            logger.warn("Received status for unknown controller: {} in orchestration: {}", controllerName, orchestrationId);
            return;
        }
        
        state.setStatus(status);
        state.setInstanceId(instanceId);
        state.setLastUpdateTime(System.currentTimeMillis());
        state.setSummary(summary);
        state.setErrorMessage(errorMessage);
        
        logger.info("Controller status update: {} - {} - {}", controllerName, status, summary);
        
        // Check if orchestration is complete
        if (context.allControllersCompleted() && !context.isCompleted()) {
            completeOrchestration(context);
        }
    }
    
    /**
     * Periodic check for orchestration status and timeouts.
     */
    private void checkOrchestrationStatus() {
        try {
            for (OrchestrationContext context : activeOrchestrations.values()) {
                if (context.isCompleted()) {
                    continue;
                }
                
                // Check for timeout
                if (context.isTimedOut()) {
                    logger.warn("Orchestration timed out: {}", context.getOrchestrationId());
                    completeOrchestration(context);
                }
                
                // Check if all controllers completed
                else if (context.allControllersCompleted()) {
                    logger.info("All controllers completed for orchestration: {}", context.getOrchestrationId());
                    completeOrchestration(context);
                }
            }
        } catch (Exception e) {
            logger.error("Error checking orchestration status", e);
        }
    }
    
    /**
     * Complete an orchestration and emit final event.
     */
    private void completeOrchestration(OrchestrationContext context) {
        if (context.isCompleted()) {
            return; // Already completed
        }
        
        context.setCompleted(true);
        
        int totalControllers = context.getControllers().size();
        int completedControllers = context.getCompletedCount();
        int failedControllers = context.getFailedCount();
        boolean success = failedControllers == 0 && completedControllers == totalControllers;
        
        // Aggregate results
        Map<String, String> results = new HashMap<>();
        for (Map.Entry<String, ControllerState> entry : context.getControllerStates().entrySet()) {
            ControllerState state = entry.getValue();
            String result = state.getStatus();
            if (state.getErrorMessage() != null) {
                result += " - " + state.getErrorMessage();
            } else if (state.getSummary() != null) {
                result += " - " + state.getSummary();
            }
            results.put(entry.getKey(), result);
        }
        
        String summary = String.format("Orchestration completed: %d/%d succeeded, %d failed, timeout: %b",
                completedControllers, totalControllers, failedControllers, context.isTimedOut());
        
        logger.info("Completing orchestration: {} - {}", context.getOrchestrationId(), summary);
        
        // Publish completed event
        eventPublisher.publishCompletedEvent(context.getOrchestrationId(), success, results, summary);
        
        // Schedule cleanup after a delay
        executorService.submit(() -> {
            try {
                Thread.sleep(60000); // Keep context for 1 minute
                activeOrchestrations.remove(context.getOrchestrationId());
                logger.info("Cleaned up orchestration context: {}", context.getOrchestrationId());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }
    
    /**
     * Get active orchestration context.
     */
    public OrchestrationContext getOrchestration(String orchestrationId) {
        return activeOrchestrations.get(orchestrationId);
    }
    
    /**
     * Get all active orchestrations.
     */
    public Collection<OrchestrationContext> getActiveOrchestrations() {
        return new ArrayList<>(activeOrchestrations.values());
    }
    
    /**
     * Shutdown the orchestrator.
     */
    public void shutdown() {
        logger.info("Shutting down Orchestrator");
        
        statusMonitor.shutdown();
        executorService.shutdown();
        
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
            if (!statusMonitor.awaitTermination(5, TimeUnit.SECONDS)) {
                statusMonitor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executorService.shutdownNow();
            statusMonitor.shutdownNow();
            logger.warn("Shutdown interrupted");
        }
        
        logger.info("Orchestrator shut down");
    }
}
