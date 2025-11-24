package org.vericrop.service.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Base controller that can be orchestrated via Kafka commands.
 * Subclasses implement the actual controller logic in executeTask().
 */
public abstract class BaseController {
    
    protected final Logger logger;
    protected final String controllerName;
    protected final ExecutorService executor;
    protected final ControllerStatusPublisher statusPublisher;
    
    /**
     * Interface for publishing controller status events.
     */
    public interface ControllerStatusPublisher {
        void publishStatus(String orchestrationId, String controllerName, String instanceId,
                          String status, String summary, String errorMessage);
    }
    
    /**
     * Constructor.
     * 
     * @param controllerName Name of this controller
     * @param statusPublisher Publisher for status events
     */
    protected BaseController(String controllerName, ControllerStatusPublisher statusPublisher) {
        this.logger = LoggerFactory.getLogger(getClass());
        this.controllerName = controllerName;
        this.statusPublisher = statusPublisher;
        this.executor = Executors.newCachedThreadPool();
        logger.info("{} controller initialized", controllerName);
    }
    
    /**
     * Handle start command from orchestrator.
     * 
     * @param orchestrationId Orchestration ID
     * @param parameters Command parameters
     */
    public void handleStartCommand(String orchestrationId, Map<String, Object> parameters) {
        String instanceId = UUID.randomUUID().toString();
        
        logger.info("{} received start command: orchestrationId={}, instanceId={}",
                controllerName, orchestrationId, instanceId);
        
        // Publish started status
        publishStatus(orchestrationId, instanceId, "started", "Controller task started", null);
        
        // Execute task asynchronously
        CompletableFuture.runAsync(() -> {
            try {
                // Execute the actual controller logic
                String result = executeTask(orchestrationId, parameters);
                
                // Publish completed status
                publishStatus(orchestrationId, instanceId, "completed", result, null);
                
                logger.info("{} completed successfully: orchestrationId={}, result={}",
                        controllerName, orchestrationId, result);
                
            } catch (Exception e) {
                // Publish failed status
                String errorMessage = "Task failed: " + e.getMessage();
                publishStatus(orchestrationId, instanceId, "failed", null, errorMessage);
                
                logger.error("{} failed: orchestrationId={}", controllerName, orchestrationId, e);
            }
        }, executor);
    }
    
    /**
     * Execute the controller task. Subclasses implement this method.
     * 
     * @param orchestrationId Orchestration ID
     * @param parameters Task parameters
     * @return Result summary
     * @throws Exception if task fails
     */
    protected abstract String executeTask(String orchestrationId, Map<String, Object> parameters) throws Exception;
    
    /**
     * Publish status event.
     */
    protected void publishStatus(String orchestrationId, String instanceId, String status,
                                 String summary, String errorMessage) {
        if (statusPublisher != null) {
            try {
                statusPublisher.publishStatus(orchestrationId, controllerName, instanceId,
                        status, summary, errorMessage);
            } catch (Exception e) {
                logger.error("Failed to publish status", e);
            }
        }
    }
    
    /**
     * Shutdown the controller.
     */
    public void shutdown() {
        logger.info("Shutting down {} controller", controllerName);
        executor.shutdown();
    }
}
