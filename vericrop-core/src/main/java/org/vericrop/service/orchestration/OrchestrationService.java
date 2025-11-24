package org.vericrop.service.orchestration;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.service.orchestration.ScenarioOrchestrationResult.ExecutionStatus;
import org.vericrop.service.orchestration.ScenarioOrchestrationResult.ScenarioExecutionStatus;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Orchestrates concurrent execution of multiple scenario groups.
 * 
 * This service manages the parallel execution of six scenario groups:
 * scenarios, delivery, map, temperature, supplierCompliance, and simulations.
 * 
 * It uses CompletableFuture for concurrency, Kafka for event-driven communication,
 * and HTTP triggers for Airflow DAG execution.
 * 
 * Thread-safe and designed for high-throughput operations.
 */
public class OrchestrationService {
    private static final Logger logger = LoggerFactory.getLogger(OrchestrationService.class);
    private static final long DEFAULT_TIMEOUT_MS = 60000; // 60 seconds
    private static final int THREAD_POOL_SIZE = 10;
    
    private final ExecutorService executorService;
    private final KafkaEventPublisher kafkaPublisher;
    private final AirflowDagTrigger airflowTrigger;
    private final ObjectMapper objectMapper;
    
    /**
     * Constructs an OrchestrationService with the specified components.
     * 
     * @param kafkaPublisher Kafka publisher for scenario events
     * @param airflowTrigger Airflow DAG trigger for analytics workflows
     */
    public OrchestrationService(KafkaEventPublisher kafkaPublisher, AirflowDagTrigger airflowTrigger) {
        this.executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        this.kafkaPublisher = kafkaPublisher;
        this.airflowTrigger = airflowTrigger;
        this.objectMapper = new ObjectMapper();
        
        logger.info("OrchestrationService initialized with pool size: {}", THREAD_POOL_SIZE);
    }
    
    /**
     * Runs multiple scenario groups concurrently and returns aggregated results.
     * 
     * Each scenario group is executed in parallel using CompletableFuture.
     * The method waits for all scenarios to complete or timeout, then aggregates
     * the results into a single response object.
     * 
     * @param request The orchestration request containing scenario groups and context
     * @return Orchestration result with per-scenario status and overall success
     */
    public ScenarioOrchestrationResult runConcurrentScenarios(ScenarioOrchestrationRequest request) {
        if (request == null || request.getScenarioGroups() == null || request.getScenarioGroups().isEmpty()) {
            throw new IllegalArgumentException("Request and scenario groups must not be null or empty");
        }
        
        Instant startTime = Instant.now();
        String requestId = request.getRequestId();
        
        logger.info("Starting orchestration for request: {} with {} scenario groups",
                requestId, request.getScenarioGroups().size());
        
        // Create a map to store execution statuses
        Map<ScenarioGroup, ScenarioExecutionStatus> scenarioStatuses = new ConcurrentHashMap<>();
        
        // Create CompletableFutures for each scenario group
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (ScenarioGroup scenarioGroup : request.getScenarioGroups()) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                executeScenarioGroup(scenarioGroup, request, scenarioStatuses);
            }, executorService);
            
            futures.add(future);
        }
        
        // Wait for all futures to complete or timeout
        try {
            long timeoutMs = request.getTimeoutMs() > 0 ? request.getTimeoutMs() : DEFAULT_TIMEOUT_MS;
            
            CompletableFuture<Void> allOf = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0]));
            
            allOf.get(timeoutMs, TimeUnit.MILLISECONDS);
            
            logger.info("All scenario groups completed for request: {}", requestId);
            
        } catch (TimeoutException e) {
            logger.warn("Orchestration timed out for request: {}", requestId);
            
            // Mark incomplete scenarios as timeout
            for (ScenarioGroup group : request.getScenarioGroups()) {
                scenarioStatuses.computeIfAbsent(group, g -> new ScenarioExecutionStatus(
                        g,
                        ExecutionStatus.TIMEOUT,
                        "Execution timed out",
                        Instant.now(),
                        "Timeout after " + request.getTimeoutMs() + "ms"
                ));
            }
            
        } catch (Exception e) {
            logger.error("Error during orchestration for request: {}", requestId, e);
            
            // Mark incomplete scenarios as failed
            for (ScenarioGroup group : request.getScenarioGroups()) {
                scenarioStatuses.computeIfAbsent(group, g -> new ScenarioExecutionStatus(
                        g,
                        ExecutionStatus.FAILURE,
                        "Orchestration error",
                        Instant.now(),
                        e.getMessage()
                ));
            }
        }
        
        Instant endTime = Instant.now();
        
        ScenarioOrchestrationResult result = new ScenarioOrchestrationResult(
                requestId,
                startTime,
                endTime,
                scenarioStatuses
        );
        
        logger.info("Orchestration completed for request: {} - Success: {}, Duration: {}ms",
                requestId, result.isOverallSuccess(), result.getDurationMs());
        
        return result;
    }
    
    /**
     * Executes a single scenario group.
     * 
     * This method publishes Kafka events and triggers Airflow DAGs for the scenario group.
     * Results are stored in the provided concurrent map for thread-safe access.
     * 
     * @param scenarioGroup The scenario group to execute
     * @param request The orchestration request
     * @param statusMap The concurrent map to store execution status
     */
    private void executeScenarioGroup(
            ScenarioGroup scenarioGroup,
            ScenarioOrchestrationRequest request,
            Map<ScenarioGroup, ScenarioExecutionStatus> statusMap) {
        
        logger.info("Executing scenario group: {} for request: {}",
                scenarioGroup, request.getRequestId());
        
        try {
            // Publish Kafka event for the scenario group
            boolean kafkaSuccess = publishKafkaEvent(scenarioGroup, request);
            
            // Trigger Airflow DAG for analytics/simulation workflows
            boolean airflowSuccess = triggerAirflowDag(scenarioGroup, request);
            
            // Determine overall success
            boolean success = kafkaSuccess && airflowSuccess;
            
            ScenarioExecutionStatus status = new ScenarioExecutionStatus(
                    scenarioGroup,
                    success ? ExecutionStatus.SUCCESS : ExecutionStatus.PARTIAL,
                    success ? "Completed successfully" : "Partial success (Kafka: " + kafkaSuccess + ", Airflow: " + airflowSuccess + ")",
                    Instant.now(),
                    null
            );
            
            statusMap.put(scenarioGroup, status);
            
            logger.info("Scenario group {} completed - Status: {}",
                    scenarioGroup, status.getStatus());
            
        } catch (Exception e) {
            logger.error("Failed to execute scenario group: {}", scenarioGroup, e);
            
            ScenarioExecutionStatus status = new ScenarioExecutionStatus(
                    scenarioGroup,
                    ExecutionStatus.FAILURE,
                    "Execution failed",
                    Instant.now(),
                    e.getMessage()
            );
            
            statusMap.put(scenarioGroup, status);
        }
    }
    
    /**
     * Publishes a Kafka event for the scenario group.
     * 
     * @param scenarioGroup The scenario group
     * @param request The orchestration request
     * @return true if event was published successfully
     */
    private boolean publishKafkaEvent(ScenarioGroup scenarioGroup, ScenarioOrchestrationRequest request) {
        if (kafkaPublisher == null) {
            logger.warn("Kafka publisher not configured, skipping event for: {}", scenarioGroup);
            return true; // Don't fail if Kafka is not configured
        }
        
        try {
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("requestId", request.getRequestId());
            eventData.put("userId", request.getUserId());
            eventData.put("scenarioGroup", scenarioGroup.name());
            eventData.put("timestamp", Instant.now().toString());
            eventData.put("context", request.getContext());
            
            kafkaPublisher.publish(scenarioGroup.getKafkaTopic(), eventData);
            
            logger.debug("Published Kafka event for scenario group: {} to topic: {}",
                    scenarioGroup, scenarioGroup.getKafkaTopic());
            
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to publish Kafka event for scenario group: {}", scenarioGroup, e);
            return false;
        }
    }
    
    /**
     * Triggers an Airflow DAG for the scenario group.
     * 
     * @param scenarioGroup The scenario group
     * @param request The orchestration request
     * @return true if DAG was triggered successfully
     */
    private boolean triggerAirflowDag(ScenarioGroup scenarioGroup, ScenarioOrchestrationRequest request) {
        if (airflowTrigger == null) {
            logger.warn("Airflow trigger not configured, skipping DAG for: {}", scenarioGroup);
            return true; // Don't fail if Airflow is not configured
        }
        
        try {
            Map<String, Object> dagConf = new HashMap<>();
            dagConf.put("requestId", request.getRequestId());
            dagConf.put("userId", request.getUserId());
            dagConf.put("scenarioGroup", scenarioGroup.name());
            dagConf.put("context", request.getContext());
            
            boolean triggered = airflowTrigger.triggerDag(
                    scenarioGroup.getAirflowDagName(),
                    dagConf
            );
            
            if (triggered) {
                logger.debug("Triggered Airflow DAG: {} for scenario group: {}",
                        scenarioGroup.getAirflowDagName(), scenarioGroup);
            }
            
            return triggered;
            
        } catch (Exception e) {
            logger.error("Failed to trigger Airflow DAG for scenario group: {}", scenarioGroup, e);
            return false;
        }
    }
    
    /**
     * Shuts down the orchestration service and releases resources.
     */
    public void shutdown() {
        logger.info("Shutting down OrchestrationService");
        
        executorService.shutdown();
        
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executorService.shutdownNow();
            logger.warn("Shutdown interrupted");
        }
        
        logger.info("OrchestrationService shut down");
    }
    
    /**
     * Interface for Kafka event publishing.
     */
    public interface KafkaEventPublisher {
        void publish(String topic, Map<String, Object> eventData) throws Exception;
    }
    
    /**
     * Interface for Airflow DAG triggering.
     */
    public interface AirflowDagTrigger {
        boolean triggerDag(String dagName, Map<String, Object> dagConf) throws Exception;
    }
}
